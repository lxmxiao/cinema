package com.stylefeng.guns.rest.mq;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class Producer {

    @Value("${mq.nameserver.addr}")
    private String addr;

    @Value("${mq.topic}")
    private String topic;

    private DefaultMQProducer producer;

    // 注册时初始化
    @PostConstruct
    public void init() throws MQClientException {
        producer = new DefaultMQProducer("producer_group");
        producer.setNamesrvAddr(addr);
        producer.start();
        log.info("Producer 初始化成功 adr：{}",addr);
    }

    public void decreaseStock(Integer promoId, Integer amount) throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        Map<String, Object> map = new HashMap<>();
        map.put("promoId",promoId);
        map.put("amount",amount);
        String body = JSON.toJSONString(map);
        Message message = new Message(topic, body.getBytes(Charset.forName("utf-8")));
        SendResult send = producer.send(message);
        log.info("Producer 已发送 发送的数据为：{}",body);
    }

}
