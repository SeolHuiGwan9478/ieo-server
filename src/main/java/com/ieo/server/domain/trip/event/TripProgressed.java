package com.ieo.server.domain.trip.event;

import java.time.Instant;

/**
 * 운송 중 진행률이 50%, 67%를 처음 넘었다 (TRIP-012). 주선사·수하인 ETA 알림(09 알림)이 받아서 쓴다.
 * 아직 받는 곳은 없다
 */
public record TripProgressed(Long tripId, int percent, Instant at) {
}
