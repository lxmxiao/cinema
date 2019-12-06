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
import java.util.Date;
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

    @Value("${mq.consumergroup}")
    private String groupName;

    private DefaultMQPushConsumer mqPushConsumer;

    @Autowired
    private MtimePromoStockMapper stockMapper;

    // 注册时初始化
    @PostConstruct
    public void init() throws MQClientException {
        mqPushConsumer = new DefaultMQPushConsumer("consumer_group");
        mqPushConsumer.setNamesrvAddr(addr);
        mqPushConsumer.subscribe(topic,"*");
        mqPushConsumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> list, ConsumeConcurrentlyContext consumeConcurrentlyContext) {



                // 获取提供者传过来的参数
                MessageExt messageExt = list.get(0);
                byte[] body = messageExt.getBody();
                String bodyString = new String(body);

                HashMap hashMap = JSON.parseObject(bodyString, HashMap.class);
                Integer promoId = (Integer) hashMap.get("promoId");
                Integer amount = (Integer) hashMap.get("amount");

                log.info("消息接收，promoId:{},amount:{}   " + new Date().toString(),promoId,amount);

                // 更改mysql数据库中的数据

                Integer integer = stockMapper.decreaseStock(promoId, amount);
                if (integer < 1){
                    log.info("消费失败！扣减库存失败，promoId:{},amount:{}",promoId,amount);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;

//                EntityWrapper<MtimePromoStock> mtimePromoStockEntityWrapper = new EntityWrapper<>();
//                MtimePromoStock mtimePromoStock = promoStockMapper.selectList(mtimePromoStockEntityWrapper).get(0);
//                mtimePromoStockEntityWrapper.eq("promo_id",promoId);
//                int stock = mtimePromoStock.getStock() - amount;
//                mtimePromoStock.setStock(stock);
//                promoStockMapper.update(mtimePromoStock,mtimePromoStockEntityWrapper);
//
//                log.info("收到消息，并修改数据库成功，promoId:{}， amount:{}",promoId,amount);
//
//                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        mqPushConsumer.start();
    }
}
