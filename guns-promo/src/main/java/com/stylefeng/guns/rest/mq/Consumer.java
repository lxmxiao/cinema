package com.stylefeng.guns.rest.mq;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.stylefeng.guns.rest.common.persistence.dao.MtimePromoStockMapper;
import com.stylefeng.guns.rest.common.persistence.model.MtimePromoStock;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;

@Component
@Slf4j
public class Consumer {

    @Autowired
    private MtimePromoStockMapper promoStockMapper;

    @Value("${mq.nameserver.addr}")
    private String addr;

    @Value("${mq.topic}")
    private String topic;

    private DefaultMQPushConsumer consumer;

    // 注册时初始化
    @PostConstruct
    public void init() throws MQClientException {
        consumer = new DefaultMQPushConsumer("consumer_group");
        consumer.setNamesrvAddr(addr);
        consumer.subscribe(topic,"*");
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> list, ConsumeConcurrentlyContext consumeConcurrentlyContext) {

                // 获取提供者传过来的参数
                MessageExt messageExt = list.get(0);
                byte[] body = messageExt.getBody();
                String bodyString = new String(body);
                HashMap hashMap = JSON.parseObject(bodyString, HashMap.class);
                Integer promoId = (Integer) hashMap.get("promoId");
                Integer amount = (Integer) hashMap.get("amount");

                // 更改mysql数据库中的数据
                EntityWrapper<MtimePromoStock> mtimePromoStockEntityWrapper = new EntityWrapper<>();
                MtimePromoStock mtimePromoStock = promoStockMapper.selectList(mtimePromoStockEntityWrapper).get(0);
                mtimePromoStockEntityWrapper.eq("promo_id",promoId);
                int stock = mtimePromoStock.getStock() - amount;
                mtimePromoStock.setStock(stock);
                promoStockMapper.update(mtimePromoStock,mtimePromoStockEntityWrapper);

                log.info("收到消息，并修改数据库成功，promoId:{}， amount:{}",promoId,amount);

                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer.start();
    }
}
