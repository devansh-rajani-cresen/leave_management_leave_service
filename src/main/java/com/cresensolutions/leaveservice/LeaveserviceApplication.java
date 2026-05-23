package com.cresensolutions.leaveservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.cresensolutions.leaveservice.client")
@EnableAspectJAutoProxy
public class LeaveserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeaveserviceApplication.class, args);
	}

}
