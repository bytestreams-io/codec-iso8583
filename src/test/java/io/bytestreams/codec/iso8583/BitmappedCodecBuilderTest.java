package io.bytestreams.codec.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.bytestreams.codec.core.CodecException;
import io.bytestreams.codec.core.Codecs;
import io.bytestreams.codec.core.SequentialObjectCodec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class BitmappedCodecBuilderTest {

  private final SequentialObjectCodec<TestMessage> codec =
      BitmappedCodecBuilder.<TestMessage>builder(TestMessage::new)
          .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap)
          .skip("extension indicator") // bit 1
          .dataField(
              "field2", Codecs.ascii(3), TestMessage::getField2, TestMessage::setField2) // bit 2
          .dataField(
              "field3", Codecs.ascii(5), TestMessage::getField3, TestMessage::setField3) // bit 3
          .build();

  // -- decode --

  private static byte[] bitmap(int firstByte) {
    byte[] bytes = new byte[8];
    bytes[0] = (byte) firstByte;
    return bytes;
  }

  private static byte[] concat(byte[]... arrays) {
    int length = 0;
    for (byte[] array : arrays) {
      length += array.length;
    }
    byte[] result = new byte[length];
    int pos = 0;
    for (byte[] array : arrays) {
      System.arraycopy(array, 0, result, pos, array.length);
      pos += array.length;
    }
    return result;
  }

  @Test
  void decode_all_fields_present() throws IOException {
    // bits 2+3 set: 0110_0000 = 0x60
    byte[] data = concat(bitmap(0x60), "ABC".getBytes(), "DEFGH".getBytes());

    TestMessage msg = codec.decode(new ByteArrayInputStream(data));

    assertThat(msg.getBitmap().get(2)).isTrue();
    assertThat(msg.getBitmap().get(3)).isTrue();
    assertThat(msg.getField2()).isEqualTo("ABC");
    assertThat(msg.getField3()).isEqualTo("DEFGH");
  }

  // -- encode --

  @Test
  void decode_no_fields_present() throws IOException {
    TestMessage msg = codec.decode(new ByteArrayInputStream(bitmap(0x00)));

    assertThat(msg.getBitmap().cardinality()).isZero();
    assertThat(msg.getField2()).isNull();
    assertThat(msg.getField3()).isNull();
  }

  @Test
  void decode_skips_absent_field() throws IOException {
    // only bit 3 set: 0010_0000 = 0x20
    byte[] data = concat(bitmap(0x20), "DEFGH".getBytes());

    TestMessage msg = codec.decode(new ByteArrayInputStream(data));

    assertThat(msg.getBitmap().get(2)).isFalse();
    assertThat(msg.getBitmap().get(3)).isTrue();
    assertThat(msg.getField2()).isNull();
    assertThat(msg.getField3()).isEqualTo("DEFGH");
  }

  // -- skip --

  @Test
  void encode_all_fields_present() throws IOException {
    TestMessage msg = new TestMessage();
    msg.getBitmap().set(2);
    msg.getBitmap().set(3);
    msg.setField2("ABC");
    msg.setField3("DEFGH");

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(msg, output);

    assertThat(output.toByteArray())
        .isEqualTo(concat(bitmap(0x60), "ABC".getBytes(), "DEFGH".getBytes()));
  }

  @Test
  void encode_no_fields_present() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(new TestMessage(), output);

    assertThat(output.toByteArray()).isEqualTo(bitmap(0x00));
  }

  @Test
  void skip_throws_on_decode() {
    // bit 1 set: 1000_0000 = 0x80
    ByteArrayInputStream input = new ByteArrayInputStream(bitmap(0x80));
    assertThatThrownBy(() -> codec.decode(input))
        .isInstanceOf(CodecException.class)
        .hasMessageContaining("codec not implemented");
  }

  // -- field before bitmap --

  @Test
  void skip_throws_on_encode() {
    TestMessage msg = new TestMessage();
    msg.getBitmap().set(1);
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    assertThatThrownBy(() -> codec.encode(msg, output))
        .isInstanceOf(CodecException.class)
        .hasMessageContaining("method for skipped field should never be called");
  }

  // -- multiBlockBitmap --

  @Test
  void unreachable_setter_throws() {
    assertThatThrownBy(() -> BitmappedCodecBuilder.DataFieldBuilder.unreachable(null, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void unreachable_getter_throws() {
    assertThatThrownBy(() -> BitmappedCodecBuilder.DataFieldBuilder.unreachable(null))
        .isInstanceOf(IllegalStateException.class);
  }

  // -- helpers --

  @Test
  void field_before_bitmap() throws IOException {
    SequentialObjectCodec<TestMessage> codecWithMti =
        BitmappedCodecBuilder.<TestMessage>builder(TestMessage::new)
            .field("mti", Codecs.ascii(4), TestMessage::getMti, TestMessage::setMti)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap)
            .dataField(
                "field2", Codecs.ascii(3), TestMessage::getField2, TestMessage::setField2) // bit 1
            .build();

    // bit 1 set: 1000_0000 = 0x80
    byte[] data = concat("0200".getBytes(), bitmap(0x80), "ABC".getBytes());

    TestMessage decoded = codecWithMti.decode(new ByteArrayInputStream(data));

    assertThat(decoded.getMti()).isEqualTo("0200");
    assertThat(decoded.getField2()).isEqualTo("ABC");

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codecWithMti.encode(decoded, output);

    assertThat(output.toByteArray()).isEqualTo(data);
  }

  @Test
  void multi_block_bitmap() throws IOException {
    SequentialObjectCodec<MultiBlockMessage> multiCodec =
        BitmappedCodecBuilder.<MultiBlockMessage>builder(MultiBlockMessage::new)
            .multiBlockBitmap(8, MultiBlockMessage::getBitmap, MultiBlockMessage::setBitmap)
            .dataField(
                "field2",
                Codecs.ascii(3),
                MultiBlockMessage::getField2,
                MultiBlockMessage::setField2) // bit 2 (bit 1 auto-skipped)
            .build();

    // bit 2 set: 0100_0000 = 0x40
    byte[] data = concat(bitmap(0x40), "ABC".getBytes());

    MultiBlockMessage decoded = multiCodec.decode(new ByteArrayInputStream(data));

    assertThat(decoded.getBitmap().get(2)).isTrue();
    assertThat(decoded.getField2()).isEqualTo("ABC");
  }

  @Test
  void multi_block_bitmap_auto_skips_extension_indicators() throws IOException {
    SequentialObjectCodec<MultiBlockMessage> multiCodec =
        BitmappedCodecBuilder.<MultiBlockMessage>builder(MultiBlockMessage::new)
            .multiBlockBitmap(1, MultiBlockMessage::getBitmap, MultiBlockMessage::setBitmap)
            .dataField(
                "field2",
                Codecs.ascii(3),
                MultiBlockMessage::getField2,
                MultiBlockMessage::setField2)
            .build();

    byte[] data = new byte[] {0x40, 'A', 'B', 'C'};

    MultiBlockMessage decoded = multiCodec.decode(new ByteArrayInputStream(data));

    assertThat(decoded.getBitmap().get(2)).isTrue();
    assertThat(decoded.getField2()).isEqualTo("ABC");

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    multiCodec.encode(decoded, output);
    assertThat(output.toByteArray()).isEqualTo(data);
  }

  // -- test fixture --

  static class MultiBlockMessage implements Bitmapped {
    private MultiBlockBitmap bitmap = new MultiBlockBitmap(8);
    private String field2;

    @Override
    public MultiBlockBitmap getBitmap() {
      return bitmap;
    }

    public void setBitmap(MultiBlockBitmap bitmap) {
      this.bitmap = bitmap;
    }

    public String getField2() {
      return field2;
    }

    public void setField2(String field2) {
      this.field2 = field2;
    }
  }

  static class TestMessage implements Bitmapped {
    private SingleBlockBitmap bitmap = new SingleBlockBitmap(8);
    private String mti;
    private String field2;
    private String field3;

    @Override
    public SingleBlockBitmap getBitmap() {
      return bitmap;
    }

    public void setBitmap(SingleBlockBitmap bitmap) {
      this.bitmap = bitmap;
    }

    public String getMti() {
      return mti;
    }

    public void setMti(String mti) {
      this.mti = mti;
    }

    public String getField2() {
      return field2;
    }

    public void setField2(String field2) {
      this.field2 = field2;
    }

    public String getField3() {
      return field3;
    }

    public void setField3(String field3) {
      this.field3 = field3;
    }
  }
}
