package com.IDP.alert_service.controller;

import com.IDP.alert_service.model.AlertMessage;
import com.IDP.alert_service.service.AlertService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/alerts")
@CrossOrigin(origins = "*") // Prevent CORS issues with your frontend
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    // 🚨 The "produces" parameter turns this normal HTTP endpoint into a live stream!
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AlertMessage> streamAlerts() {
        return alertService.getAlertStream();
    }
}
