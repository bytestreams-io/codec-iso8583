package io.bytestreams.codec.core;

/**
 * Extension point for codecs to participate in field inspection.
 *
 * <p>Built-in composite codecs implement this interface to provide structured representations of
 * their values. Custom codec implementations can also implement this interface.
 *
 * @param <T> the type of the value to inspect
 */
public interface Inspectable<T> {

  /**
   * Returns a structured representation of the given value.
   *
   * @param value the decoded value to inspect
   * @return a structured representation (Map, List, or scalar)
   */
  Object inspect(T value);
}
