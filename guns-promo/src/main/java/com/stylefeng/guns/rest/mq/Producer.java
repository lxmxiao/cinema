package com.stylefeng.guns.rest.mq;

import com.alibaba.fastjson.JSON;
import com.stylefeng.guns.rest.common.persistence.dao.MtimeStockLogMapper;
import com.stylefeng.guns.rest.common.persistence.model.MtimeStockLog;
import com.stylefeng.guns.rest.service.PromoService;
import com.stylefeng.guns.rest.vo.promo.PromoOrderVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class Producer {

    @Value("${mq.nameserver.addr}")
    private String addr;

    @Value("${mq.topic}")
    private String topic;

    @Value("${mq.producergroup}")
    private String groupName;

    @Value("${mq.transactionproducergroup}")
    private String transactiongroup;

    private DefaultMQProducer producer;

    private TransactionMQProducer transactionMQProducer;

    @Autowired
    private PromoService promoService;

    @Autowired
    private MtimeStockLogMapper stockLogMapper;

    // 注册时初始化
    @PostConstruct
    public void init() throws MQClientException {
        producer = new DefaultMQProducer(groupName);
        producer.setNamesrvAddr(addr);
        producer.start();
        log.info("mqProducer启动成功...");

        transactionMQProducer = new TransactionMQProducer(transactiongroup);
        transactionMQProducer.setNamesrvAddr(addr);
        transactionMQProducer.start();
        log.info("transactionMQProducer启动成功...");

        // 事务监听回调器
        transactionMQProducer.setTransactionListener(new TransactionListener() {

            //第一个方法 执行本地事务
            @Override
            public LocalTransactionState executeLocalTransaction(Message message, Object o) {
                log.info("执行本地事务" + new Date().toString());

                HashMap hashMap = (HashMap) o;
                Integer promoId = (Integer) hashMap.get("promoId");
                Integer amount = (Integer) hashMap.get("amount");
                Integer userId = (Integer) hashMap.get("userId");
                String stockLogId = (String) hashMap.get("stockLogId");

                // 执行本地事务，插入订单，扣减redis库存
                PromoOrderVO promoOrderVO = promoService.savePromoOrderVO(promoId,amount,userId,stockLogId);

                if (promoOrderVO == null){
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            //回查本地事务状态
            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt messageExt) {

                log.info("执行回查本地事务状态" + new Date().toString());

                byte[] body = messageExt.getBody();
                String bodyStr = new String(body);
                HashMap hashMap = JSON.parseObject(bodyStr, HashMap.class);

                String stockLogId = (String) hashMap.get("stockLogId");
                MtimeStockLog stockLog = stockLogMapper.selectById(stockLogId);

                Integer status = stockLog.getStatus();
                //如果status 是成功 表示本地事务执行成功
                if (status == 2){
                    return LocalTransactionState.COMMIT_MESSAGE;
                }
                //如果status是失败，表示本地事务执行失败
                if (status == 3) {
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }

                return LocalTransactionState.UNKNOW;
            }
        });
    }



    /**
     * 发送事务性消息
     * @param promoId
     * @param amount
     * @param userId
     * @param stockLogId
     * @return
     */
    public Boolean sendStockMessageIntransaction(Integer promoId, Integer amount, Integer userId, String stockLogId) {
        Map<String, Object> hashMap = new HashMap<>();
        Map<String, Object> argsMap = new HashMap<>();
        hashMap.put("promoId",promoId);
        argsMap.put("promoId",promoId);
        hashMap.put("amount",amount);
        argsMap.put("amount",amount);
        hashMap.put("userId",userId);
        argsMap.put("userId",userId);
        hashMap.put("stockLogId",stockLogId);
        argsMap.put("stockLogId",stockLogId);

        String jsonString = JSON.toJSONString(hashMap);
        Message message = new Message(topic, jsonString.getBytes(Charset.forName("utf-8")));

        TransactionSendResult transactionSendResult = null;
        try {
            transactionSendResult = transactionMQProducer.sendMessageInTransaction(message, argsMap);
            log.info("消息发出" + new Date().toString());
        } catch (MQClientException e) {
            e.printStackTrace();
            log.info("发送事务型消息—stock异常");
        }

        if (transactionSendResult == null){
            return false;
        }
        //本地事务执行状态
        LocalTransactionState localTransactionState = transactionSendResult.getLocalTransactionState();
        if (LocalTransactionState.COMMIT_MESSAGE.equals(localTransactionState)) {
            return true;
        }else{
            return false;
        }
    }

//    public void decreaseStock(Integer promoId, Integer amount) throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
//        Map<String, Object> map = new HashMap<>();
//        map.put("promoId",promoId);
//        map.put("amount",amount);
//        String body = JSON.toJSONString(map);
//        Message message = new Message(topic, body.getBytes(Charset.forName("utf-8")));
//        SendResult send = producer.send(message);
//        log.info("Producer 已发送 发送的数据为：{}",body);
//    }

}
