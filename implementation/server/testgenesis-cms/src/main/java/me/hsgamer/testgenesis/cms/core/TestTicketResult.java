package me.hsgamer.testgenesis.cms.core;

public record TestTicketResult(boolean accepted, String reason, TestSession session) implements TicketResult {

}
