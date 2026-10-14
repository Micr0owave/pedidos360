package cl.duoc.p360.mqadmin.dto;

public record QueueInfoResponse(String name, int messageCount, int consumerCount) {
}
