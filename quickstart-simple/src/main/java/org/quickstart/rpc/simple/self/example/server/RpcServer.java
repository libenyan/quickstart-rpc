/**
 * 项目名称：quickstart-rpc-simple 
 * 文件名：RpcServer.java
 * 版本信息：
 * 日期：2018年4月24日
 * Copyright asiainfo Corporation 2018
 * 版权所有 *
 */
package org.quickstart.rpc.simple.self.example.server;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.quickstart.rpc.simple.self.example.common.RpcService;
import org.quickstart.rpc.simple.self.example.util.RpcDecoder;
import org.quickstart.rpc.simple.self.example.util.RpcEncoder;
import org.quickstart.rpc.simple.self.example.util.RpcRequest;
import org.quickstart.rpc.simple.self.example.util.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * RpcServer 
 *  
 * @author：yangzl@asiainfo.com
 * @2018年4月24日 下午12:31:09 
 * @since 1.0
 */
/**
 * 启动并注册服务
 */
public class RpcServer implements ApplicationContextAware, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcServer.class);

    private String serverAddress;
    private ServiceRegistry serviceRegistry;

    private Map<String, Object> handlerMap = new HashMap<>(); // 存放接口名与服务对象之间的映射关系

    public RpcServer(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public RpcServer(String serverAddress, ServiceRegistry serviceRegistry) {
        this.serverAddress = serverAddress;
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        Map<String, Object> serviceBeanMap = ctx.getBeansWithAnnotation(RpcService.class); // 获取所有带有 RpcService 注解的 Spring Bean
        if (MapUtils.isNotEmpty(serviceBeanMap)) {
            for (Object serviceBean : serviceBeanMap.values()) {
                String interfaceName = serviceBean.getClass().getAnnotation(RpcService.class).value().getName();
                handlerMap.put(interfaceName, serviceBean);
            }
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel channel) throws Exception {
                    channel.pipeline().addLast(new RpcDecoder(RpcRequest.class)) // 将 RPC 请求进行解码（为了处理请求）
                            .addLast(new RpcEncoder(RpcResponse.class)) // 将 RPC 响应进行编码（为了返回响应）
                            .addLast(new RpcHandler(handlerMap)); // 处理 RPC 请求
                }
            }).option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true);

            String[] array = serverAddress.split(":");
            String host = array[0];
            int port = Integer.parseInt(array[1]);

            ChannelFuture future = bootstrap.bind(host, port).sync();
            LOGGER.debug("server started on port {}", port);

            if (serviceRegistry != null) {
                serviceRegistry.register(serverAddress); // 注册服务地址
            }

            future.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
