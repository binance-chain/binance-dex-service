package com.binance.dex.service.rest;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.dex.api.client.BinanceDexApi;
import com.binance.dex.api.client.BinanceDexApiClientGenerator;
import com.binance.dex.api.client.Wallet;
import com.binance.dex.api.client.domain.TransactionMetadata;
import com.binance.dex.api.client.domain.broadcast.CancelOrder;
import com.binance.dex.api.client.domain.broadcast.MultiTransfer;
import com.binance.dex.api.client.domain.broadcast.NewOrder;
import com.binance.dex.api.client.domain.broadcast.TokenFreeze;
import com.binance.dex.api.client.domain.broadcast.TokenUnfreeze;
import com.binance.dex.api.client.domain.broadcast.TransactionOption;
import com.binance.dex.api.client.domain.broadcast.Transfer;
import com.binance.dex.api.client.domain.broadcast.Vote;

import okhttp3.RequestBody;

@RestController
public class RestService {
    
    private static final Logger log = LoggerFactory.getLogger(RestService.class);
    
    private static final okhttp3.MediaType MEDIA_TYPE = okhttp3.MediaType.parse("text/plain; charset=utf-8");
    
    @Autowired
    private ApplicationConfiguration configuration;
    
    @Autowired
    private TransactionLogic transactionLogic;
    
    private static String getClientIp(HttpServletRequest request) {
        String remoteAddr = "";
        if (request != null) {
            remoteAddr = request.getHeader("X-FORWARDED-FOR");
            if (remoteAddr == null || "".equals(remoteAddr)) {
                remoteAddr = request.getRemoteAddr();
            }
        }
        return remoteAddr;
    }
    
    private static TransactionOption getTransacationOption(String memo, Long source, String data) throws DexServiceException {
        TransactionOption options = TransactionOption.DEFAULT_INSTANCE;
        if (memo != null) {
            options.setMemo(memo);
        }
        if (source != null) {
            options.setSource(source);
        }
        if (data != null) {
            try {
                options.setData(Hex.decodeHex(data));
            } catch (DecoderException ex) {
                throw new DexServiceException("Error in parameter data (should be in hexadecimal format). " + ex.getMessage(), ex);
            }
        }
        return options;
    }
    
    @RequestMapping("/")
    public String index() {
        log.info("index requested");
        log.info("env: {}", configuration.getBinanceDexEnvironment());
        return "REST service";
    }
    
    @RequestMapping("/newOrder")
    public String newOrder(
            @RequestParam(name = "name") String name,
            @RequestParam(name = "pin") Integer pin,
            NewOrder newOrder, Wallet wallet, TransactionOption option) {
        log.info("New Order");
        return "newOrder";
    }
    
    public String vote(
            @RequestParam(name = "name") String name,
            @RequestParam(name = "pin") Integer pin,
            Vote vote, Wallet wallet, TransactionOption options) {
        return "vote";
    }
    
    @RequestMapping("/cancelOrder")
    public String cancelOrder(
            @RequestParam(name = "name") String name,
            @RequestParam(name = "pin") Integer pin,
            CancelOrder cancelOrder, Wallet wallet, TransactionOption options) {
        log.info("Cancel Order");
        return "cancelOrder";
    }
    
    @RequestMapping("/transfer")
    public String transfer(
            @RequestParam(name = "name") String name,
            @RequestParam(name = "pin") Integer pin,
            @RequestParam(name = "coin") String coin,
            @RequestParam(name = "toAddress") String toAddress,
            @RequestParam(name = "amount") String amount,
            @RequestParam(name = "memo", required = false) String memo,
            @RequestParam(name = "source", required = false) Long source,
            @RequestParam(name = "data", required = false) String data,
            @RequestParam(name = "detail", required = false, defaultValue = "false") Boolean detail,
            HttpServletRequest request) throws DexServiceException {
        String clientIp = getClientIp(request);
        Wallet wallet = configuration.getWallet(name, pin, clientIp);
        
        Transfer transfer = new Transfer();
        transfer.setFromAddress(wallet.getAddress());
        transfer.setToAddress(toAddress);
        transfer.setCoin(coin);
        transfer.setAmount(amount);
        
        TransactionOption options = getTransacationOption(memo, source, data);
        String transferPayload = transactionLogic.transfer(wallet, transfer, options);
        log.info("Transfer clientIp: {} transferPayload: {}", clientIp, transferPayload);
        return transferPayload;
    }
    
    @RequestMapping("/multiTransfer")
    public String multiTransfer(
            @RequestParam(name = "name") String name,
            @RequestParam(name = "pin") Integer pin,
            MultiTransfer multiTransfer, Wallet wallet, TransactionOption options) {
        return "multiTransfer";
    }
    
    @RequestMapping("/freeze")
    public String freeze(
            @RequestParam(name = "name") String name,
            @RequestParam(name = "pin") Integer pin,
            TokenFreeze freeze, Wallet wallet, TransactionOption options) {
        return "freeze";
    }
    
    @RequestMapping("/unfreeze")
    public String unfreeze(
            @RequestParam(name = "name") String name,
            @RequestParam(name = "pin") Integer pin,
            TokenUnfreeze unfreeze, Wallet wallet, TransactionOption options) {
        return "unfreeze";
    }
    
    @RequestMapping("/broadcast")
    public List<TransactionMetadata> broadcast(
            @RequestParam(name = "name") String name,
            @RequestParam(name = "pin") Integer pin,
            @RequestParam(name = "payload") String payload,
            HttpServletRequest request) throws DexServiceException {
        String clientIp = getClientIp(request);
        Wallet wallet = configuration.getWallet(name, pin, clientIp);
        
        RequestBody requestBody = RequestBody.create(MEDIA_TYPE, payload);
        BinanceDexApi binanceDexApi = BinanceDexApiClientGenerator.createService(BinanceDexApi.class, configuration.getBinanceDexEnvironment().getBaseUrl());
        List<TransactionMetadata> metadatas = BinanceDexApiClientGenerator.executeSync(binanceDexApi.broadcast(true, requestBody));
        if (!metadatas.isEmpty() && metadatas.get(0).isOk()) {
            wallet.increaseAccountSequence();
        }
        return metadatas;
    }
}