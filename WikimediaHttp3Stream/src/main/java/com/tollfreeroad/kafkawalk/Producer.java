package com.tollfreeroad.kafkawalk;

import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.StreamException;
import okhttp3.HttpUrl;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.launchdarkly.eventsource.EventSource;

import java.util.Properties;

public class Producer {
    private static final Logger log = LoggerFactory.getLogger(Producer.class.getSimpleName());

    public static void main(String[] args) {
        Properties props = new Properties();

//      Server properties ================================
//      props.setProperty("bootstrap.servers", "localhost:9094");
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9094");

//      Producer properties ==============================
//      props.setProperty("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getCanonicalName());
//      props.setProperty("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getCanonicalName());
        props.setProperty(ProducerConfig.LINGER_MS_CONFIG, "20");
        props.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(32 * 1024));
        props.setProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

//      Get producer =====================================
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);

        String topic = "wikimedia.recentchange";
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("stream.wikimedia.org")
                .addPathSegment("v2")
                .addPathSegment("stream")
                .addPathSegment("recentchange")
                .build();
        EventSource builder = new EventSource.Builder(url).build();

//      Stream to producer ===============================
        try {
            builder.start();
            for(MessageEvent m: builder.messages()) {
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, "oct-2024", m.getData());
                producer.send(record, new Callback() {
                    @Override
                    public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                        if(e != null) {
                            e.printStackTrace();
                        }
                        else {
                            log.info("Partition = "+recordMetadata.partition());
                            log.info("Offset = "+recordMetadata.offset());
                            log.info("topic = "+recordMetadata.topic());
                        }
                    }
                });
            }
        }
        catch(ProducerFencedException | AuthorizationException | OutOfOrderSequenceException e) {
            log.info("Kafka exception occurred ==============");
            e.printStackTrace();
        }
        catch(KafkaException e) {
            e.printStackTrace();
        } catch (StreamException e) {
            throw new RuntimeException(e);
        } finally {
            log.info("Shutting down the stream");
            builder.close();
            producer.close();
        }
    }
}