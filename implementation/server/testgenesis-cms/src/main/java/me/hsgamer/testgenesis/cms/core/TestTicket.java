package me.hsgamer.testgenesis.cms.core;

import me.hsgamer.testgenesis.uap.v1.Payload;

import java.util.List;

public record TestTicket(String testType, List<Payload> payloads) {
}
