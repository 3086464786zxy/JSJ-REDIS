package com.itmk.config.redis;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;


/**
 * Redis配置类
 *
 * 作用：
 * 1. 配置RedisTemplate
 * 2. 设置Redis Key采用String序列化
 * 3. 设置Redis Value采用Jackson JSON序列化
 * 4. 支持Java对象直接存入Redis
 */
@Configuration
public class RedisConfig {


    /**
     * 自定义RedisTemplate
     *
     * @param factory Redis连接工厂
     * @return RedisTemplate<String,Object>
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            LettuceConnectionFactory factory) {


        /*
         * 创建RedisTemplate对象
         *
         * RedisTemplate负责：
         * Java对象 <----> Redis数据之间的转换
         */
        RedisTemplate<String, Object> template =
                new RedisTemplate<>();


        /*
         * 设置Redis连接工厂
         *
         * Spring Boot默认使用Lettuce作为Redis客户端
         */
        template.setConnectionFactory(factory);



        /*
         * 创建Jackson ObjectMapper
         *
         * ObjectMapper是Jackson核心类：
         * 负责Java对象和JSON之间的转换
         */
        ObjectMapper mapper = new ObjectMapper();



        /*
         * 注册Java8时间模块
         *
         * 支持：
         * LocalDate
         * LocalDateTime
         * LocalTime
         *
         * 如果不添加该模块，
         * Redis存储包含日期类型的对象时会报错
         */
        mapper.registerModule(
                new JavaTimeModule()
        );

        /*
         * 注册 Spring Security 提供的 Jackson MixIn。
         * SimpleGrantedAuthority 没有无参构造方法，必须通过该模块才能正确反序列化。
         */
        mapper.registerModules(
                SecurityJackson2Modules.getModules(
                        RedisConfig.class.getClassLoader()
                )
        );



        /*
         * 忽略JSON中不存在于Java对象的字段
         *
         * 例如Redis中：
         *
         * {
         *    "id":1,
         *    "name":"张三",
         *    "age":20
         * }
         *
         * Java对象：
         *
         * User{
         *    id;
         *    name;
         * }
         *
         * 不会因为age字段不存在而报错
         */
        mapper.configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
        );



        /*
         * 配置Jackson类型校验器
         *
         * 作用：
         * 限制哪些Java类型允许被反序列化
         *
         * 例如：
         * 只允许com.itmk包下的类
         *
         * 防止Redis数据被篡改导致反序列化安全问题
         */
        PolymorphicTypeValidator validator =
                BasicPolymorphicTypeValidator.builder()

                        // 允许项目自身的对象反序列化
                        .allowIfSubType("com.itmk.")

                        // SysUser.authorities 的集合实现类型（如 ArrayList）
                        .allowIfSubType("java.util.")

                        // Spring Security 的权限实现类型（如 SimpleGrantedAuthority）
                        .allowIfSubType("org.springframework.security.core.authority.")

                        .build();




        /*
         * 开启Jackson默认类型信息
         *
         * 为什么需要？
         *
         * Redis存储：
         *
         * {
         *    "name":"张三",
         *    "age":20
         * }
         *
         * Jackson不知道它原来的类型：
         *
         * User?
         * Order?
         * Product?
         *
         *
         * 开启后：
         *
         * {
         *   "@class":"com.itmk.entity.User",
         *   "name":"张三",
         *   "age":20
         * }
         *
         * Redis读取时可以恢复成原Java对象
         */
        mapper.activateDefaultTyping(
                validator,

                /*
                 * NON_FINAL:
                 *
                 * 对非final类型添加类型信息
                 *
                 * 比如：
                 * User
                 * DTO
                 * List
                 */
                ObjectMapper.DefaultTyping.NON_FINAL,


                /*
                 * 类型信息存储方式
                 *
                 * JSON属性形式：
                 *
                 * "@class":"xxx.User"
                 */
                JsonTypeInfo.As.PROPERTY
        );




        /*
         * 创建Jackson Redis序列化器
         *
         * Object.class表示：
         *
         * 可以序列化任意Java对象
         *
         * 例如：
         * User
         * Order
         * List<User>
         */
        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(
                        mapper,
                        Object.class
                );




        /*
         * 创建String序列化器
         *
         * Redis中的Key一般都是字符串：
         *
         * user:1
         * login:token:xxx
         *
         */
        StringRedisSerializer string =
                new StringRedisSerializer();




        /*
         * 设置Key序列化方式
         *
         * 如果不设置：
         *
         * Redis:
         *
         * \xac\xed\x00\x05user:1
         *
         *
         * 设置后：
         *
         * user:1
         */
        template.setKeySerializer(string);



        /*
         * 设置Hash结构中的key序列化方式
         *
         * 例如：
         *
         * redisTemplate.opsForHash()
         * .put("user","1",user)
         */
        template.setHashKeySerializer(string);




        /*
         * 设置Value序列化方式
         *
         * Java对象：
         *
         * User对象
         *
         * 转换成：
         *
         * JSON
         */
        template.setValueSerializer(serializer);



        /*
         * 设置Hash中的value序列化方式
         */
        template.setHashValueSerializer(serializer);




        /*
         * 初始化RedisTemplate配置
         *
         * 必须调用，否则部分配置不会生效
         */
        template.afterPropertiesSet();



        return template;
    }

}
