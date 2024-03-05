package com.foxapplication.mc.interconnectionplayer.common.util;

import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;
import com.esotericsoftware.kryo.kryo5.serializers.BeanSerializer;
import lombok.Getter;
import lombok.Setter;

/**
 * KryoUtil是一个用于序列化和反序列化对象的工具类。
 *
 * @param <T> 要序列化和反序列化的对象的类型
 */
public class KryoUtil<T> {

    /**
     * 用于序列化对象的输出流
     */
    private final ThreadLocal<Output> outputLocal = ThreadLocal.withInitial(() -> new Output(4096, -1));

    /**
     * 用于反序列化对象的输入流
     */
    private final ThreadLocal<Input> inputLocal = ThreadLocal.withInitial(Input::new);

    /**
     * 要序列化和反序列化的对象的类型
     */
    @Setter
    @Getter
    private Class<T> ct;

    /**
     * 用于序列化和反序列化对象的Kryo实例
     */
    private final ThreadLocal<Kryo> kryoLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.register(byte[].class);
        kryo.register(String.class);
        kryo.register(Long.class);
        kryo.register(Boolean.class);
        kryo.register(ct, new BeanSerializer<>(kryo, ct));
        return kryo;
    });


    /**
     * 构造一个KryoUtil对象。
     *
     * @param ct 要序列化和反序列化的对象的类型
     */
    public KryoUtil(Class<T> ct) {
        this.ct = ct;
    }

    /**
     * 将对象序列化为字节数组。
     *
     * @param obj 要序列化的对象
     * @return 序列化后的字节数组
     */
    public byte[] serialize(T obj) {
        Kryo kryo = kryoLocal.get();
        Output output = outputLocal.get();
        output.reset();
        kryo.writeObjectOrNull(output, obj, ct);
        return output.toBytes();
    }

    /**
     * 将对象序列化为指定的字节数组。
     *
     * @param obj   要序列化的对象
     * @param bytes 存储序列化结果的字节数组
     */
    public void serialize(T obj, byte[] bytes) {
        Kryo kryo = kryoLocal.get();
        Output output = outputLocal.get();
        output.setBuffer(bytes);
        kryo.writeObjectOrNull(output, obj, ct);
    }

    /**
     * 将对象序列化为指定的字节数组的一部分。
     *
     * @param obj   要序列化的对象
     * @param bytes 存储序列化结果的字节数组
     * @param count 序列化结果的长度
     */
    public void serialize(T obj, byte[] bytes, int count) {
        Kryo kryo = kryoLocal.get();
        Output output = outputLocal.get();
        output.setBuffer(bytes, count);
        kryo.writeObjectOrNull(output, obj, ct);
    }

    /**
     * 将字节数组反序列化为对象。
     *
     * @param bytes 要反序列化的字节数组
     * @return 反序列化后的对象
     */
    public T deserialize(byte[] bytes) {
        return deserialize(bytes, 0, bytes.length);
    }

    /**
     * 将字节数组的一部分反序列化为对象。
     *
     * @param bytes  要反序列化的字节数组
     * @param offset 要反序列化的字节数组的起始位置
     * @param count  要反序列化的字节数组的长度
     * @return 反序列化后的对象
     */
    public T deserialize(byte[] bytes, int offset, int count) {
        Kryo kryo = kryoLocal.get();
        Input input = inputLocal.get();
        input.setBuffer(bytes, offset, count);
        return kryo.readObjectOrNull(input, ct);
    }
}