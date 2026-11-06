# Requirements

## Goal

Dealerships need one API to book service appointments without double booking technicians, bays or vehicles.
Clients are dealership booking systems and customer apps. They call the API at booking time.

## MVP

Rules and status codes live in [system-design.md](system-design.md), section 2. They are not repeated here.

| # | Feature | Done when |
|---|---|---|
| 1 | Book an appointment | When a valid request arrives and a qualified technician and a bay are free, then the API returns 201 with the chosen technician and bay, and a `Location` header. |
| 2 | Reject bad bookings | When a request breaks a rule in system-design section 2, then the API returns that status and reason as a ProblemDetail. |
| 3 | No double booking | When N requests ask for the same start at the same dealership at the same time, then no technician, bay or vehicle is ever double booked, as many requests as there are free slots (at most N) get 201, and the others get 409 with a reason, or 503 if their wait in line goes over 2 s. |
| 4 | Safe retry | When the same booking is sent again (same dealership, vehicle, start, service type), then the API returns 200 with the existing appointment, also while the first request is still running. |
| 5 | Read an appointment | When `GET /appointments/{id}` is called with a known id, then it returns 200 with the appointment. Unknown id: 404. |

For the whole project:

- `docker compose up` starts the API and PostgreSQL.
- Swagger UI works as the client stub.
- GitHub Actions runs build and all tests on each push.
- `/actuator/health` checks the database. Logs are structured, carry a request id, and hold no personal data.
- Latency of `POST /appointments` is measured with Micrometer.

## Not in MVP

See [system-design.md](system-design.md), section 11. In short: no UI, no authentication, no cancel or reschedule, no live push.

## Submission

From the Keyloop brief: a system design document, this repository (README with build, run and test steps and an AI collaboration narrative, plus tests of the core business logic), and a 5 to 10 minute video. Due 2 November 2026.
