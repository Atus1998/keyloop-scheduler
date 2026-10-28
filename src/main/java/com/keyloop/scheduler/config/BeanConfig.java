package com.keyloop.scheduler.config;

import com.keyloop.scheduler.application.port.in.GetAppointmentUseCase;
import com.keyloop.scheduler.application.port.out.AppointmentPort;
import com.keyloop.scheduler.application.service.GetAppointmentService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the application services, which carry no Spring annotations, to their ports. */
@Configuration(proxyBeanMethods = false)
class BeanConfig {

  @Bean
  GetAppointmentUseCase getAppointmentUseCase(AppointmentPort appointments) {
    return new GetAppointmentService(appointments);
  }
}
