package com.keyloop.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Checks the GitHub Actions workflow file. It proves the shape only; the first run on GitHub proves
 * GitHub accepts it.
 */
class CiWorkflowTest {

  private static final Path WORKFLOW = Path.of(".github/workflows/ci.yml");
  private static final String BUILD_COMMAND = "./gradlew build";
  private static final List<String> TRIGGER_FILTERS =
      List.of("branches", "branches-ignore", "tags", "tags-ignore", "paths", "paths-ignore");

  /** Best effort: the usual ways to hide a failed build or skip tests, not every one. */
  private static final List<String> FAILURE_HIDERS =
      List.of(
          "|| true",
          "|| :",
          "|| echo",
          "set +e",
          "-x ",
          "--exclude-task",
          "-DskipTests",
          "-PskipTests",
          "--continue");

  private static final Pattern PINNED_ACTION =
      Pattern.compile("^[\\w.-]+/[\\w.-]+(/[\\w.-]+)*@[0-9a-f]{40}$");
  private static final Pattern USES_LINE = Pattern.compile("^\\s*(- )?uses: .*");
  private static final Pattern VERSION_COMMENT = Pattern.compile(".* # v\\S+$");
  private static final int MAX_REPORT_RETENTION_DAYS = 14;

  @Test
  void runsOnPushToEveryBranch() throws IOException {
    Map<String, Object> triggers = triggers();

    assertThat(triggers).containsKey("push");
    assertThat(filtersOf(triggers.get("push"))).isEmpty();
  }

  @Test
  void runsOnPullRequest() throws IOException {
    Map<String, Object> triggers = triggers();

    assertThat(triggers).containsKey("pull_request");
    assertThat(filtersOf(triggers.get("pull_request"))).isEmpty();
  }

  @Test
  void buildStepRunsGradleBuild() throws IOException {
    assertThat(buildStep()).doesNotContainKeys("if", "continue-on-error");
  }

  @Test
  void buildFailureFailsTheJob() throws IOException {
    Map<String, Object> job = buildJob();

    assertThat(job).doesNotContainKeys("continue-on-error", "if");
    assertThat(steps()).allSatisfy(step -> assertThat(step).doesNotContainKey("continue-on-error"));
    assertThat(runCommands())
        .allSatisfy(run -> assertThat(run).doesNotContain(FAILURE_HIDERS.toArray(String[]::new)));
  }

  @Test
  void usesTemurin21OnUbuntuLatest() throws IOException {
    Map<String, Object> setupJava = stepUsing("actions/setup-java");

    assertThat(buildJob()).containsEntry("runs-on", "ubuntu-latest");
    Map<String, Object> with = map(setupJava.get("with"));
    assertThat(with).containsEntry("distribution", "temurin");
    assertThat(String.valueOf(with.get("java-version"))).isEqualTo("21");
  }

  @Test
  void newerPushCancelsRunningRun() throws IOException {
    Map<String, Object> concurrency = map(workflow().get("concurrency"));

    assertThat(String.valueOf(concurrency.get("group"))).contains("github.ref");
    assertThat(concurrency).containsEntry("cancel-in-progress", true);
  }

  @Test
  void tokenIsReadOnlyAndNoSecretsUsed() throws IOException {
    assertThat(map(workflow().get("permissions"))).isEqualTo(Map.of("contents", "read"));
    assertThat(buildJob()).doesNotContainKey("permissions");
    assertThat(Files.readString(WORKFLOW)).doesNotContain("secrets.");
  }

  @Test
  void uploadsTestReportsEvenWhenBuildFails() throws IOException {
    Map<String, Object> upload = stepUsing("actions/upload-artifact");

    assertThat(upload).containsEntry("if", "always()");
    assertThat(String.valueOf(map(upload.get("with")).get("path")))
        .contains("build/reports/tests", "build/test-results");
  }

  @Test
  void testReportsExpireWithinTwoWeeks() throws IOException {
    Object retention = map(stepUsing("actions/upload-artifact").get("with")).get("retention-days");

    assertThat(retention).isInstanceOf(Integer.class);
    assertThat((Integer) retention).isBetween(1, MAX_REPORT_RETENTION_DAYS);
  }

  @Test
  void actionsArePinnedByCommitSha() throws IOException {
    List<String> actions =
        steps().stream()
            .map(step -> step.get("uses"))
            .filter(Objects::nonNull)
            .map(String::valueOf)
            .toList();

    assertThat(actions).isNotEmpty().allMatch(uses -> PINNED_ACTION.matcher(uses).matches());
  }

  @Test
  void pinnedActionsNameTheirVersion() throws IOException {
    List<String> usesLines =
        Files.readAllLines(WORKFLOW).stream()
            .filter(line -> USES_LINE.matcher(line).matches())
            .toList();

    assertThat(usesLines).isNotEmpty().allMatch(line -> VERSION_COMMENT.matcher(line).matches());
  }

  @Test
  void gradleWrapperIsValidated() throws IOException {
    Map<String, Object> setupGradle = stepUsing("gradle/actions/setup-gradle");

    assertThat(map(setupGradle.get("with"))).containsEntry("validate-wrappers", true);
  }

  @Test
  void stepsRunInOrder() throws IOException {
    List<String> order =
        steps().stream()
            .map(step -> step.containsKey("run") ? "run: " + step.get("run") : actionOf(step))
            .toList();

    assertThat(order)
        .containsSubsequence(
            "actions/checkout",
            "actions/setup-java",
            "gradle/actions/setup-gradle",
            "run: " + BUILD_COMMAND,
            "actions/upload-artifact");
  }

  private static Map<String, Object> workflow() throws IOException {
    assertThat(WORKFLOW).as("workflow file").exists();
    return map(new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(WORKFLOW)));
  }

  /** YAML 1.1 reads the key {@code on} as the boolean {@code true}. */
  private static Map<String, Object> triggers() throws IOException {
    Map<String, Object> root = workflow();
    return map(root.containsKey("on") ? root.get("on") : root.get(Boolean.TRUE));
  }

  /** A bare {@code push:} parses as null, which means no filter. */
  private static List<String> filtersOf(Object trigger) {
    if (trigger == null) {
      return List.of();
    }
    Map<String, Object> settings = map(trigger);
    return TRIGGER_FILTERS.stream().filter(settings::containsKey).toList();
  }

  private static Map<String, Object> buildJob() throws IOException {
    return map(map(workflow().get("jobs")).get("build"));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> steps() throws IOException {
    Object steps = buildJob().get("steps");
    assertThat(steps).as("steps of job build").isInstanceOf(List.class);
    return ((List<Object>) steps).stream().map(CiWorkflowTest::map).toList();
  }

  private static Map<String, Object> buildStep() throws IOException {
    return steps().stream()
        .filter(step -> BUILD_COMMAND.equals(step.get("run")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no step runs " + BUILD_COMMAND));
  }

  private static List<String> runCommands() throws IOException {
    return steps().stream()
        .map(step -> step.get("run"))
        .filter(Objects::nonNull)
        .map(String::valueOf)
        .toList();
  }

  private static Map<String, Object> stepUsing(String action) throws IOException {
    return steps().stream()
        .filter(step -> action.equals(actionOf(step)))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no step uses " + action));
  }

  private static String actionOf(Map<String, Object> step) {
    String uses = String.valueOf(step.get("uses"));
    int at = uses.indexOf('@');
    return at < 0 ? uses : uses.substring(0, at);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    assertThat(value).isInstanceOf(Map.class);
    return (Map<String, Object>) value;
  }
}
