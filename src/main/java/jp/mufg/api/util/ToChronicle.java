package jp.mufg.api.util;

import net.openhft.chronicle.Chronicle;
import net.openhft.chronicle.ExcerptAppender;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ToChronicle implements InvocationHandler {
    private final Chronicle chronicle;

    public ToChronicle(Chronicle chronicle) {
        this.chronicle = chronicle;
    }

    public static <T> T of(Class<T> interfaceType, Chronicle chroncile) throws IOException {
        return (T) Proxy.newProxyInstance(interfaceType.getClassLoader(),
                new Class[]{interfaceType},
                new ToChronicle(chroncile));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }
        ExcerptAppender appender = chronicle.createAppender();
        appender.startExcerpt();
        MetaData.get().writeMarshallable(appender);
        appender.writeUTFΔ(method.getName());
        if (args == null) {
            appender.writeStopBit(0);
        } else {
            appender.writeStopBit(args.length);
            for (Object arg : args) {
                appender.writeObject(arg);
            }
        }
        appender.finish();
        return null;
    }
}

