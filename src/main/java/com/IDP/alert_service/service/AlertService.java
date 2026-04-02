package com.IDP.alert_service.service;

import com.IDP.alert_service.model.AlertMessage;
import org.springframework.data.domain.Range;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;

@Service
public class AlertService {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    private final Sinks.Many<AlertMessage> alertSink = Sinks.many().multicast().onBackpressureBuffer();

    private static final String GEO_KEY = "vehicle_locations";
    private static final String PREDICTED_GEO_KEY = "predicted_locations";
    private static final double COLLISION_THRESHOLD_METERS = 200.0;

    public AlertService(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private record ScannedVehicle(String sessionId, String type, double lat, double lon, double speed) {}

    public Flux<AlertMessage> getAlertStream() {
        AlertMessage welcomeMessage = new AlertMessage(
                "SYSTEM",
                "system-node",
                "System Connected. Radar sweeping every 3 seconds...",
                System.currentTimeMillis()
        );
        return alertSink.asFlux().startWith(welcomeMessage);
    }

    @Scheduled(fixedRate = 3000)
    public void runSecuritySweep() {

        Mono<List<ScannedVehicle>> activeVehiclesMono = fetchVehiclesFromSet(GEO_KEY);
        Mono<List<ScannedVehicle>> predictedVehiclesMono = fetchVehiclesFromSet(PREDICTED_GEO_KEY);

        Mono.zip(activeVehiclesMono, predictedVehiclesMono)
                .subscribe(
                        tuple -> {
                            List<ScannedVehicle> activeVehicles = tuple.getT1();
                            List<ScannedVehicle> predictedVehicles = tuple.getT2();

                            // --- RULE 1: STATIONARY VEHICLES ---
                            for (ScannedVehicle vehicle : activeVehicles) {
                                if (vehicle.speed() == 0.0) {
                                    String msg = vehicle.type().toUpperCase() + " (" + vehicle.sessionId() + ") is stationary.";
                                    System.out.println("🚨 " + msg);

                                    alertSink.tryEmitNext(new AlertMessage(
                                            "STATIONARY",
                                            vehicle.sessionId(),
                                            msg,
                                            System.currentTimeMillis()
                                    ));
                                }
                            }

                            // --- RULE 2: RELATIVE DISTANCES ---
                            for (ScannedVehicle active : activeVehicles) {
                                for (ScannedVehicle ghost : predictedVehicles) {

                                    if (active.sessionId().equals(ghost.sessionId())) continue;

                                    double distance = calculateDistance(
                                            active.lat(), active.lon(),
                                            ghost.lat(), ghost.lon()
                                    );

                                    if (distance < COLLISION_THRESHOLD_METERS) {
                                        String msg = "Proximity Alert! " + String.format("%.2f", distance) + "m from Ghost.";
                                        System.out.println("⚠️ " + msg);

                                        alertSink.tryEmitNext(new AlertMessage(
                                                "COLLISION_WARNING",
                                                active.sessionId(),
                                                msg,
                                                System.currentTimeMillis()
                                        ));
                                    }
                                }
                            }
                        },
                        err -> System.err.println("❌ Error during radar sweep: " + err.getMessage())
                );
    }

    // 🚨 NEW PARSER LOGIC: Now fetches the speed from the separate telemetry key!
    private Mono<List<ScannedVehicle>> fetchVehiclesFromSet(String key) {
        return redisTemplate.opsForZSet().range(key, Range.unbounded())
                .flatMap(memberObj -> {
                    String memberString = String.valueOf(memberObj);
                    return redisTemplate.opsForGeo().position(key, memberString)
                            .map(pointObj -> {
                                Point point = (Point) pointObj;
                                String[] parts = memberString.replace("\"", "").trim().split(":");

                                String type = parts.length > 0 ? parts[0] : "unknown";
                                String sessionId = parts.length > 1 ? parts[1] : "unknown";
                                double speed = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.0;

                                return new ScannedVehicle(sessionId, type, point.getY(), point.getX(), speed);
                            });
                })
                .collectList();
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double EARTH_RADIUS_METERS = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }
}