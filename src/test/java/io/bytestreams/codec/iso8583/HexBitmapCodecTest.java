package io.bytestreams.codec.iso8583;

import static org.assertj.core.api.Assertions.assertThat;

import io.bytestreams.codec.core.Codec;
import io.bytestreams.codec.core.Codecs;
import io.bytestreams.codec.core.FieldSpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class HexBitmapCodecTest {

  @Test
  void hex_single_block_roundtrip() throws IOException {
    Codec<SingleBlockBitmap> hexCodec = FieldCodecs.hexSingleBlockBitmap(8);
    SingleBlockBitmap bitmap = new SingleBlockBitmap(8);
    bitmap.set(2);
    bitmap.set(3);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    hexCodec.encode(bitmap, out);
    assertThat(out).hasToString("6000000000000000");

    SingleBlockBitmap decoded = hexCodec.decode(new ByteArrayInputStream(out.toByteArray()));
    assertThat(decoded.get(2)).isTrue();
    assertThat(decoded.get(3)).isTrue();
    assertThat(decoded.cardinality()).isEqualTo(2);
  }

  @Test
  void hex_multi_block_roundtrip() throws IOException {
    Codec<MultiBlockBitmap> hexCodec = FieldCodecs.hexMultiBlockBitmap(8);
    MultiBlockBitmap bitmap = new MultiBlockBitmap(8);
    bitmap.set(2);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    hexCodec.encode(bitmap, out);
    assertThat(out).hasToString("4000000000000000");

    MultiBlockBitmap decoded = hexCodec.decode(new ByteArrayInputStream(out.toByteArray()));
    assertThat(decoded.get(2)).isTrue();
    assertThat(decoded.cardinality()).isEqualTo(1);
  }

  @Test
  void hex_multi_block_with_extension() throws IOException {
    Codec<MultiBlockBitmap> hexCodec = FieldCodecs.hexMultiBlockBitmap(8);
    MultiBlockBitmap bitmap = new MultiBlockBitmap(8);
    bitmap.set(2);
    bitmap.set(66);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    hexCodec.encode(bitmap, out);

    MultiBlockBitmap decoded = hexCodec.decode(new ByteArrayInputStream(out.toByteArray()));
    assertThat(decoded.get(2)).isTrue();
    assertThat(decoded.get(66)).isTrue();
  }

  @Test
  void hex_multi_block_with_max_blocks() throws IOException {
    Codec<MultiBlockBitmap> hexCodec = FieldCodecs.hexMultiBlockBitmap(8, 2);
    MultiBlockBitmap bitmap = new MultiBlockBitmap(8, 2);
    bitmap.set(2);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    hexCodec.encode(bitmap, out);

    MultiBlockBitmap decoded = hexCodec.decode(new ByteArrayInputStream(out.toByteArray()));
    assertThat(decoded.get(2)).isTrue();
    assertThat(decoded.cardinality()).isEqualTo(1);
  }

  @Test
  void hex_bitmap_with_unified_builder() throws IOException {
    FieldSpec<TestMsg, SingleBlockBitmap> bitmapSpec =
        FieldSpec.of(
            "bitmap", FieldCodecs.hexSingleBlockBitmap(8), TestMsg::getBitmap, TestMsg::setBitmap);

    BitmappedFieldSpec<TestMsg, String> field2 =
        BitmappedFieldSpec.of(
            2, FieldSpec.of("field2", Codecs.ascii(3), TestMsg::getField2, TestMsg::setField2));

    var builtCodec =
        BitmappedCodecBuilder.builder(TestMsg::new).bitmap(bitmapSpec).dataField(field2).build();

    // hex bitmap "4000000000000000" (bit 2 set) + "ABC"
    byte[] data = concat("4000000000000000".getBytes(), "ABC".getBytes());
    TestMsg decoded = builtCodec.decode(new ByteArrayInputStream(data));
    assertThat(decoded.getField2()).isEqualTo("ABC");
  }

  private static byte[] concat(byte[]... arrays) {
    int length = 0;
    for (byte[] a : arrays) {
      length += a.length;
    }
    byte[] result = new byte[length];
    int pos = 0;
    for (byte[] a : arrays) {
      System.arraycopy(a, 0, result, pos, a.length);
      pos += a.length;
    }
    return result;
  }

  static class TestMsg implements Bitmapped {
    private SingleBlockBitmap bitmap = new SingleBlockBitmap(8);
    private String field2;

    @Override
    public SingleBlockBitmap getBitmap() {
      return bitmap;
    }

    public void setBitmap(SingleBlockBitmap bitmap) {
      this.bitmap = bitmap;
    }

    public String getField2() {
      return field2;
    }

    public void setField2(String field2) {
      this.field2 = field2;
    }
  }
}
