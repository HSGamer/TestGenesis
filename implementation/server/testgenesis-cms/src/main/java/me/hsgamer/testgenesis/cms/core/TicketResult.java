package me.hsgamer.testgenesis.cms.core;

public interface TicketResult {
    boolean accepted();

    String reason();

    Session<?> session();
}
