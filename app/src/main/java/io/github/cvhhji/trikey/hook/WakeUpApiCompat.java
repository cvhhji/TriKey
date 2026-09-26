package io.github.cvhhji.trikey.hook;

import java.lang.reflect.Method;

final class WakeUpApiCompat {
    private WakeUpApiCompat() {}

    static Method findWakeUpMethod(Class<?> type) throws NoSuchMethodException {
        Method[] methods = type.getDeclaredMethods();
        for (int parameterCount : new int[]{4, 3, 2}) {
            for (Method method : methods) {
                if (hasSignature(method, parameterCount)) return method;
            }
        }
        throw new NoSuchMethodException("No supported PowerManager.wakeUp overload");
    }

    static Object[] arguments(Method method, long time, int reason, String details, int displayId) {
        if (!hasSignature(method, 4)) {
            if (hasSignature(method, 3)) return new Object[]{time, reason, details};
            if (hasSignature(method, 2)) return new Object[]{time, details};
            throw new IllegalArgumentException("Unsupported wakeUp overload: " + method);
        }
        return new Object[]{time, reason, details, displayId};
    }

    private static boolean hasSignature(Method method, int parameterCount) {
        Class<?>[] parameters = method.getParameterTypes();
        if (!"wakeUp".equals(method.getName()) || parameters.length != parameterCount
                || parameters[0] != long.class) return false;
        if (parameterCount == 2) return parameters[1] == String.class;
        if (parameters[1] != int.class || parameters[2] != String.class) return false;
        return parameterCount == 3 || parameters[3] == int.class;
    }
}
