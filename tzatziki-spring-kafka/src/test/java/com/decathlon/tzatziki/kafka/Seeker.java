package com.decathlon.tzatziki.kafka;

public interface Seeker {

    void seekToBeginning(String topic);

    void seek(String topic, int offset);

}
