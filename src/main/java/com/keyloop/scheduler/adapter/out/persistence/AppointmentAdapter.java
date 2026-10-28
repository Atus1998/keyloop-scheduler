package com.keyloop.scheduler.adapter.out.persistence;

import com.keyloop.scheduler.application.port.out.AppointmentPort;
import com.keyloop.scheduler.application.service.ResourceConflictException;
import com.keyloop.scheduler.application.service.ResourceConflictException.Kind;
import com.keyloop.scheduler.domain.Appointment;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Inserts appointments. No {@code @Transactional} here: the flush must fail inside {@link #insert}
 * so the error can be translated, not later at commit.
 */
@Component
class AppointmentAdapter implements AppointmentPort {

  private final AppointmentJpaRepository repository;

  AppointmentAdapter(AppointmentJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public Appointment insert(Appointment appointment) {
    if (appointment.id() != null) {
      throw new IllegalArgumentException(
          "A new appointment must not have an id, got " + appointment.id());
    }
    try {
      return repository.saveAndFlush(AppointmentEntity.newRow(appointment)).toDomain();
    } catch (RuntimeException e) {
      throw translate(e);
    }
  }

  @Override
  public Optional<Appointment> findById(long id) {
    return repository.findById(id).map(AppointmentEntity::toDomain);
  }

  /** 23P01 and 40P01 become {@link ResourceConflictException}; anything else is returned as is. */
  static RuntimeException translate(RuntimeException error) {
    return SqlStates.of(error)
        .<RuntimeException>map(
            state ->
                switch (state) {
                  case SqlStates.EXCLUSION_VIOLATION ->
                      new ResourceConflictException(Kind.EXCLUSION, error);
                  case SqlStates.DEADLOCK_DETECTED ->
                      new ResourceConflictException(Kind.DEADLOCK, error);
                  default -> error;
                })
        .orElse(error);
  }
}
