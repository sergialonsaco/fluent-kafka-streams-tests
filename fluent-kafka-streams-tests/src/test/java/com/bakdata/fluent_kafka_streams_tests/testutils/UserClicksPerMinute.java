package com.bakdata.fluent_kafka_streams_tests.testutils;

import com.bakdata.fluent_kafka_streams_tests.testutils.serde.JsonSerde;
import lombok.Getter;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class UserClicksPerMinute {
    @Getter
    final String inputTopic = "user-click-input";

    @Getter
    final String outputTopic = "user-click-output";

    public Properties getKafkaProperties() {
        final String brokers = "localhost:9092";
        final Properties kafkaConfig = new Properties();
        kafkaConfig.put(StreamsConfig.APPLICATION_ID_CONFIG, "user-clicks-per-minute");
        kafkaConfig.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        kafkaConfig.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.Integer().getClass().getName());
        kafkaConfig.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JsonSerde.class);
        return kafkaConfig;
    }

    public Topology getTopology() {
        final StreamsBuilder builder = new StreamsBuilder();
        final KStream<Integer, ClickEvent> clickEvents =
                builder.stream(inputTopic);

        final KTable<Windowed<Integer>, Long> counts = clickEvents
                .groupByKey()
                .windowedBy(TimeWindows.of(TimeUnit.MINUTES.toMillis(1)))  // 1 Minute in ms
                .count();

        counts.toStream()
                .map((key, value) -> KeyValue.pair(
                        key.key(),
                        new ClickOutput(key.key(), value, key.window().start())))
                .to(outputTopic, Produced.with(Serdes.Integer(), new JsonSerde<>(ClickOutput.class)));

        return builder.build();
    }

    public static void main(final String[] args) {
        final UserClicksPerMinute clickCount = new UserClicksPerMinute();
        final KafkaStreams streams = new KafkaStreams(clickCount.getTopology(), clickCount.getKafkaProperties());
        streams.start();
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }

}
