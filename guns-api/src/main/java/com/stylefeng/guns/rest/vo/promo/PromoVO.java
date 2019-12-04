package com.stylefeng.guns.rest.vo.promo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class PromoVO implements Serializable {

    private Integer uuid;
    private Integer stock;
    private Integer status;
    private Date startTime;
    private Integer price;
    private String imgAddress;
    private Date endTime;
    private String description;
    private String cinemaName;
    private Integer cinemaId;
    private String cinemaAddress;
}
