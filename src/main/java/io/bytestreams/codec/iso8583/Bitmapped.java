package io.bytestreams.codec.iso8583;

/** Interface for objects that contain a {@link Bitmap}. */
public interface Bitmapped {

  /**
   * Returns the bitmap indicating which data elements are present.
   *
   * @return the bitmap.
   */
  Bitmap getBitmap();
}
