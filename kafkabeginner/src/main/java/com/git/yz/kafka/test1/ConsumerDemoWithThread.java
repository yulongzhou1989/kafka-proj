package com.git.yz.kafka.test1;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class ConsumerDemoWithThread {
    public static void main(String [] args) {
        new ConsumerDemoWithThread().run();
    }

    private ConsumerDemoWithThread() {

    }

    private void run() {
        String bootstrapServers = "127.0.0.1:9092";
        String groupId = "my-fifth-group";
        String topic = "first_topic";
        CountDownLatch latch = new CountDownLatch(1);
        Logger logger = LoggerFactory.getLogger(ConsumerDemoWithThread.class.getName());
        Runnable myConsumerRunnable = new ConsumerRunnable(
                latch,
                topic,
                bootstrapServers,
                groupId
        );

        Thread myThread = new Thread(myConsumerRunnable);
        myThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("caught shutdown hook");
            ((ConsumerRunnable) myConsumerRunnable).shutdown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("Application has existed");
        }));

        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.info("Application got interrupted", e);
        } finally {
            logger.info("Application is closing");
        }
    }

    public class ConsumerRunnable implements Runnable {

        CountDownLatch latch;
        private KafkaConsumer<String, String> consumer;
        private Logger logger;

        public ConsumerRunnable(CountDownLatch latch, String topic, String bootstrapServer, String groupId) {
            this.latch = latch;

            Properties properties = new Properties();
            properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
            properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            this.consumer = new KafkaConsumer<String, String>(properties);
            // subscribe consumer to our topics
            consumer.subscribe(Arrays.asList(topic));
            this.logger = LoggerFactory.getLogger(ConsumerRunnable.class.getName());
        }

        @Override
        public void run() {

            try{
                while(true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record: records) {
                        this.logger.info("key: " + record.key() + ", value : " + record.value());
                        this.logger.info("partition: " + record.partition() + ", offset: " + record.offset());
                    }
                }
            } catch (WakeupException e) {
                logger.info("Received shutdown signal", e);
            } finally {
                consumer.close();
                //tell our main code we're done with the consumer
                latch.countDown();
            }
        }

        public void shutdown() {

            //This makeup method is a special method to interrupt consumer.poll()
            // it will throw the exception wakeupException
            consumer.wakeup();
        }
    }
}
