package com.sonora.service.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqConfig {

    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setMissingQueuesFatal(false);
        return factory;
    }

    @Bean
    public DirectExchange sonoraEventExchange() {
        return new DirectExchange(MqConstants.SONORA_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue songPlayQueue() {
        return QueueBuilder.durable(MqConstants.SONG_PLAY_QUEUE).build();
    }

    @Bean
    public Binding songPlayBinding() {
        return BindingBuilder.bind(songPlayQueue())
                .to(sonoraEventExchange())
                .with(MqConstants.SONG_PLAY_ROUTING_KEY);
    }

    @Bean
    public Queue playlistPlayQueue() {
        return QueueBuilder.durable(MqConstants.PLAYLIST_PLAY_QUEUE).build();
    }

    @Bean
    public Binding playlistPlayBinding() {
        return BindingBuilder.bind(playlistPlayQueue())
                .to(sonoraEventExchange())
                .with(MqConstants.PLAYLIST_PLAY_ROUTING_KEY);
    }

    @Bean
    public Queue songLikeCountQueue() {
        return QueueBuilder.durable(MqConstants.SONG_LIKE_COUNT_QUEUE).build();
    }

    @Bean
    public Binding songLikeCountBinding() {
        return BindingBuilder.bind(songLikeCountQueue())
                .to(sonoraEventExchange())
                .with(MqConstants.SONG_LIKE_COUNT_ROUTING_KEY);
    }

    @Bean
    public Queue playlistCollectCountQueue() {
        return QueueBuilder.durable(MqConstants.PLAYLIST_COLLECT_COUNT_QUEUE).build();
    }

    @Bean
    public Binding playlistCollectCountBinding() {
        return BindingBuilder.bind(playlistCollectCountQueue())
                .to(sonoraEventExchange())
                .with(MqConstants.PLAYLIST_COLLECT_COUNT_ROUTING_KEY);
    }
}
