package org.dbsyncer.common.retry;

/**
 * 可抛异常的 Supplier 接口
 */
@FunctionalInterface
public interface ThrowingSupplier<T> {
    T get() throws Exception;
}
