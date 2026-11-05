package com.keyloop.scheduler.config;

import com.keyloop.scheduler.application.port.in.BookAppointmentUseCase;
import com.keyloop.scheduler.application.port.in.GetAppointmentUseCase;
import com.keyloop.scheduler.application.port.out.AppointmentPort;
import com.keyloop.scheduler.application.port.out.AvailabilityPort;
import com.keyloop.scheduler.application.port.out.DealershipDayLockPort;
import com.keyloop.scheduler.application.port.out.ReferenceDataPort;
import com.keyloop.scheduler.application.port.out.TransactionPort;
import com.keyloop.scheduler.application.service.BookAppointmentService;
import com.keyloop.scheduler.application.service.GetAppointmentService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the application services, which carry no Spring annotations, to their ports. */
@Configuration(proxyBeanMethods = false)
class BeanConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  GetAppointmentUseCase getAppointmentUseCase(AppointmentPort appointments) {
    return new GetAppointmentService(appointments);
  }

  @Bean
  BookAppointmentUseCase bookAppointmentUseCase(
      ReferenceDataPort referenceData,
      AvailabilityPort availability,
      AppointmentPort appointments,
      TransactionPort transactions,
      DealershipDayLockPort locks,
      Clock clock) {
    return new BookAppointmentService(
        referenceData, availability, appointments, transactions, locks, clock);
  }
}
