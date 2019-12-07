package com.stylefeng.guns.rest.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.stylefeng.guns.core.exception.GunsException;
import com.stylefeng.guns.core.exception.GunsExceptionEnum;
import com.stylefeng.guns.core.support.HttpKit;
import com.stylefeng.guns.core.util.DateUtil;
import com.stylefeng.guns.rest.common.persistence.dao.MtimePromoMapper;
import com.stylefeng.guns.rest.common.persistence.dao.MtimePromoOrderMapper;
import com.stylefeng.guns.rest.common.persistence.dao.MtimePromoStockMapper;
import com.stylefeng.guns.rest.common.persistence.dao.MtimeStockLogMapper;
import com.stylefeng.guns.rest.common.persistence.model.MtimePromo;
import com.stylefeng.guns.rest.common.persistence.model.MtimePromoOrder;
import com.stylefeng.guns.rest.common.persistence.model.MtimePromoStock;
import com.stylefeng.guns.rest.common.persistence.model.MtimeStockLog;
import com.stylefeng.guns.rest.dto.PromoDTO;
import com.stylefeng.guns.rest.mq.Producer;
import com.stylefeng.guns.rest.service.CinemaService;
import com.stylefeng.guns.rest.service.PromoService;
import com.stylefeng.guns.rest.vo.BaseResponVO;
import com.stylefeng.guns.rest.vo.cinema.CinemaInfoVO;
import com.stylefeng.guns.rest.vo.promo.PromoOrderVO;
import com.stylefeng.guns.rest.vo.promo.PromoVO;
import com.stylefeng.guns.rest.vo.user.UserVO;
import com.sun.org.apache.xpath.internal.operations.Bool;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Service(interfaceClass = PromoService.class)
@Slf4j
public class PromoServiceImpl implements PromoService {

    //秒杀令牌对于库存的倍数
    public static final Integer PROMO_TOKEN_TIMES = 5;

    @Autowired
    private Producer producer;

    @Autowired
    private MtimeStockLogMapper stockLogMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MtimePromoStockMapper promoStockMapper;

    @Autowired
    private MtimePromoOrderMapper promoOrderMapper;

    @Autowired
    private MtimePromoMapper promoMapper;

    @Reference(interfaceClass = CinemaService.class, check = false)
    private CinemaService cinemaService;

    private ExecutorService executorService;

    @PostConstruct
    public void init(){
        executorService = Executors.newFixedThreadPool(100);
    }

    /**
     * 获取秒杀列表
     * @param promoDTO
     * @return
     */
    @Override
    public BaseResponVO getPromo(PromoDTO promoDTO) {
        List<MtimePromo> mtimePromos;
        if(promoDTO.getNowPage() != null || promoDTO.getPageSize() != null){
            Page<MtimePromo> mtimePromoPage = new Page<>(promoDTO.getNowPage(), promoDTO.getPageSize());
            mtimePromos = promoMapper.selectPage(mtimePromoPage, null);
        }
        mtimePromos = promoMapper.selectList(null);
        List<PromoVO> list = new ArrayList<>();
        if (mtimePromos == null){
            return new BaseResponVO(0,null,list,null,null,null);
        }

        //获取所需要显示的的数据
        for (MtimePromo mtimePromo : mtimePromos) {
            PromoVO promoVO = new PromoVO();
            CinemaInfoVO cinemaInfoVO = cinemaService.getCinemaInfoVOByCinemaId(mtimePromo.getCinemaId());
            promoVO.setCinemaId(cinemaInfoVO.getCinemaId());
            promoVO.setCinemaName(cinemaInfoVO.getCinemaName());
            promoVO.setCinemaAddress(cinemaInfoVO.getCinemaAdress());
            promoVO.setImgAddress(cinemaInfoVO.getImgUrl());
            promoVO.setDescription(mtimePromo.getDescription());
            promoVO.setEndTime(mtimePromo.getEndTime());
            promoVO.setStartTime(mtimePromo.getStartTime());
            promoVO.setPrice((int) mtimePromo.getPrice().doubleValue());
            promoVO.setStatus(mtimePromo.getStatus());
            promoVO.setUuid(mtimePromo.getUuid());

            //获取秒杀库存
//            EntityWrapper<MtimePromoStock> mtimePromoStockEntityWrapper = new EntityWrapper<>();
//            mtimePromoStockEntityWrapper.eq("promo_id",mtimePromo.getUuid());
//            MtimePromoStock mtimePromoStock = promoStockMapper.selectList(mtimePromoStockEntityWrapper).get(0);
//            promoVO.setStock(mtimePromoStock.getStock());
//            List<MtimePromoStock> promo_stock = (List<MtimePromoStock>) redisTemplate.opsForValue().get("promo_stock");
//            for (MtimePromoStock mtimePromoStock : promo_stock) {
//                if (mtimePromo.getUuid().equals(mtimePromoStock.getPromoId())){
//                    promoVO.setStock(mtimePromoStock.getStock());
//                }
//            }

            Integer stock = (Integer) redisTemplate.opsForValue().get("promo_stock" + mtimePromo.getUuid());
            promoVO.setStock(stock);

            list.add(promoVO);
        }
        return new BaseResponVO(0,null,list,null,null,null);
    }

    /**
     * 将库存信息发布到缓存
     * @return
     */
    @Override
    public BaseResponVO publishPromoStock() {

        List<MtimePromoStock> mtimePromos = promoStockMapper.selectList(null);
        for (MtimePromoStock mtimePromo : mtimePromos) {
            if (redisTemplate.opsForValue().get("promo_stock" + mtimePromo.getPromoId()) == null) {
                redisTemplate.opsForValue().set("promo_stock" + mtimePromo.getPromoId(), mtimePromo.getStock());
                redisTemplate.expire("promo_stock" + mtimePromo.getPromoId(), 365 * 100, TimeUnit.DAYS);
            }

            Integer amountValue = mtimePromo.getStock() * PROMO_TOKEN_TIMES;
            redisTemplate.opsForValue().set("promo_stock_amount" + mtimePromo.getUuid(),amountValue);
        }


        return new BaseResponVO(0,"发布成功");
    }

    /**
     * 初始化秒杀日志表
     * @param promoId
     * @param amount
     * @return
     */
    @Override
    public String initPromoStockLog(Integer promoId, Integer amount) {
        MtimeStockLog mtimeStockLog = new MtimeStockLog();
        String uuid = UUID.randomUUID().toString().replaceAll("-","").substring(0,18);
        mtimeStockLog.setUuid(uuid);
        mtimeStockLog.setAmount(amount);
        mtimeStockLog.setPromoId(promoId);
        mtimeStockLog.setStatus(1);
        Integer insert = stockLogMapper.insert(mtimeStockLog);
        if (insert > 0){
            return uuid;
        }else {
            return null;
        }
    }

    /**
     * 创建订单
     * @param promoId
     * @param amount
     * @param userId
     * @param stockLogId
     * @return
     */
    @Override
    public Boolean savePromoOrderInTransaction(Integer promoId, Integer amount, Integer userId, String stockLogId) {
        Boolean res = producer.sendStockMessageIntransaction(promoId, amount, userId,stockLogId);
        return res;
    }

    /**
     * 本地事务生成秒杀订单
     * @param promoId
     * @param amount
     * @param userId
     * @param stockLogId
     * @return
     */
    @Override
    public PromoOrderVO savePromoOrderVO(Integer promoId, Integer amount, Integer userId, String stockLogId) {

        //参数校验
        processParam(promoId,userId,amount);

        MtimePromo promo = promoMapper.selectById(promoId);

        //生成订单
        MtimePromoOrder promoOrder = savePromoOrder(promo,amount,userId);
        log.info("生成订单" + new Date().toString());

        if (promoOrder == null){
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    stockLogMapper.updateStatusById(stockLogId,3);
                }
            });
            return null;
        }

        // 扣减库存
        Boolean result = decreaseStock(promoId, amount);
        log.info("扣减库存" + new Date().toString());

        if (!result){
            executorService.submit(() ->{
                stockLogMapper.updateStatusById(stockLogId,3);
            });
            return null;
        }

        //组装参数返回前端
        PromoOrderVO promoOrderVO =  buildPromoOrderVO(promoOrder);

        //假如本地事务执行成功  更新库存流水记录的状态 -----成功
        stockLogMapper.updateStatusById(stockLogId,2);

        //返回前端
        return promoOrderVO;

    }

    /**
     * 生成秒杀令牌
     * @param promoId
     * @return
     */
    @Override
    public String generateToken(Integer promoId, UserVO user) {
        Long remainAmount = redisTemplate.opsForValue().increment("promo_stock_amount" + promoId,-1);

        if (remainAmount < 0) {
            return null;
        }

        String uuid = UUID.randomUUID().toString().replaceAll("-","").substring(0,18);

        redisTemplate.opsForValue().set("user_promo_token_" + promoId + "_userId_" + user.getUuid(),uuid);

        return uuid;
    }

    //构建返回相应参数
    private PromoOrderVO buildPromoOrderVO(MtimePromoOrder promoOrder) {
        PromoOrderVO orderVO = new PromoOrderVO();
        orderVO.setUuid(promoOrder.getUuid());
        orderVO.setUserId(promoOrder.getUserId());
        orderVO.setEndTime(promoOrder.getEndTime());
        orderVO.setStartTime(promoOrder.getStartTime());
        orderVO.setAmount(promoOrder.getAmount());
        orderVO.setCinemaId(promoOrder.getCinemaId());
        orderVO.setCreateTime(promoOrder.getCreateTime());
        orderVO.setExchangeCode(promoOrder.getExchangeCode());
        orderVO.setPrice(promoOrder.getPrice().intValue());
        return orderVO;
    }

    // 扣减库存
    private Boolean decreaseStock(Integer promoId, Integer amount) {

        String key = "promo_stock" + promoId;

        Long increment = redisTemplate.opsForValue().increment(key, amount * -1);

        if (increment < 0){
            log.info("库存已售完,promoId:{}",promoId);
            redisTemplate.opsForValue().increment(key,amount);
            return false;
        }
        return true;
    }

    //生成订单
    private MtimePromoOrder savePromoOrder(MtimePromo promo, Integer amount, Integer userId) {

        //组装promoOrder
        MtimePromoOrder promoOrder = buidPromoOrder(promo,userId,amount);

        Integer insert = promoOrderMapper.insert(promoOrder);

        if (insert < 1){
            log.info("生成秒杀订单失败！promoOrder:{}", JSON.toJSONString(promoOrder));
            return null;
        }
        return promoOrder;
    }

    private MtimePromoOrder buidPromoOrder(MtimePromo promo, Integer userId, Integer amount) {
        MtimePromoOrder promoOrder = new MtimePromoOrder();
        String uuid = UUID.randomUUID().toString().replaceAll("-","").substring(0,18);
        Integer cinemaId = promo.getCinemaId();
        String exchangeCode = UUID.randomUUID().toString().replaceAll("-","").substring(0,18);
        //兑换开始时间和兑换结束时间 为从现在开始，到未来三个月之内
        Date startTime = new Date();
        Calendar canlendar = Calendar.getInstance(); // java.util包
        canlendar.add(Calendar.MONTH, 3); // 日期减 如果不够减会将月变动
        Date endTime = canlendar.getTime();
        BigDecimal amountDecimal = new BigDecimal(amount);
        BigDecimal price = amountDecimal.multiply(promo.getPrice()).setScale(2, RoundingMode.HALF_UP);
        promoOrder.setUuid(uuid);
        promoOrder.setUserId(userId);
        promoOrder.setCinemaId(cinemaId);
        promoOrder.setExchangeCode(exchangeCode);
        promoOrder.setStartTime(startTime);
        promoOrder.setEndTime(endTime);
        promoOrder.setAmount(amount);
        promoOrder.setPrice(price);
        promoOrder.setCreateTime(new Date());
        return  promoOrder;
    }

    //参数校验
    private void processParam(Integer promoId, Integer userId, Integer amount) {
        if (promoId == null) {
            log.info("promoId不能为空！");
            throw new GunsException(GunsExceptionEnum.REQUEST_NULL);
        }
        if (userId == null) {
            log.info("userId不能为空！");
            throw new GunsException(GunsExceptionEnum.REQUEST_NULL);
        }
        if (amount == null) {
            log.info("amount不能为空！");
            throw new GunsException(GunsExceptionEnum.REQUEST_NULL);
        }
    }
}
