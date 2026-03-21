package io.bytestreams.codec.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.bytestreams.codec.core.Codec;
import io.bytestreams.codec.core.CodecException;
import io.bytestreams.codec.core.Codecs;
import io.bytestreams.codec.core.FieldSpec;
import io.bytestreams.codec.core.SequentialObjectCodec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class BitmappedCodecBuilderTest {

  static final BitmappedFieldSpec<TestMessage, String> FIELD_2 =
      BitmappedFieldSpec.of(
          2,
          FieldSpec.of("field2", Codecs.ascii(3), TestMessage::getField2, TestMessage::setField2));

  static final BitmappedFieldSpec<TestMessage, String> FIELD_3 =
      BitmappedFieldSpec.of(
          3,
          FieldSpec.of("field3", Codecs.ascii(5), TestMessage::getField3, TestMessage::setField3));
  static final BitmappedFieldSpec<MultiBlockMessage, String> MULTI_FIELD_2 =
      BitmappedFieldSpec.of(
          2,
          FieldSpec.of(
              "field2",
              Codecs.ascii(3),
              MultiBlockMessage::getField2,
              MultiBlockMessage::setField2));

  // -- decode --
  private final SequentialObjectCodec<TestMessage> codec =
      BitmappedCodecBuilder.builder(TestMessage::new)
          .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap)
          .dataField(FIELD_2)
          .dataField(FIELD_3)
          .build();

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

  @Test
  void decode_no_fields_present() throws IOException {
    TestMessage msg = codec.decode(new ByteArrayInputStream(bitmap(0x00)));

    assertThat(msg.getBitmap().cardinality()).isZero();
    assertThat(msg.getField2()).isNull();
    assertThat(msg.getField3()).isNull();
  }

  // -- encode --

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

  // -- field before bitmap --

  @Test
  void encode_no_fields_present() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(new TestMessage(), output);

    assertThat(output.toByteArray()).isEqualTo(bitmap(0x00));
  }

  // -- multi block bitmap --

  @Test
  void field_spec_before_bitmap() throws IOException {
    FieldSpec<TestMessage, String> mtiSpec =
        FieldSpec.of("mti", Codecs.ascii(4), TestMessage::getMti, TestMessage::setMti);

    SequentialObjectCodec<TestMessage> codecWithMti =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .field(mtiSpec)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap)
            .dataField(FIELD_2)
            .build();

    // bit 2 set: 0100_0000 = 0x40
    byte[] data = concat("0200".getBytes(), bitmap(0x40), "ABC".getBytes());

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
        BitmappedCodecBuilder.builder(MultiBlockMessage::new)
            .multiBlockBitmap(8, MultiBlockMessage::getBitmap, MultiBlockMessage::setBitmap)
            .dataField(MULTI_FIELD_2)
            .build();

    // bit 2 set: 0100_0000 = 0x40
    byte[] data = concat(bitmap(0x40), "ABC".getBytes());

    MultiBlockMessage decoded = multiCodec.decode(new ByteArrayInputStream(data));

    assertThat(decoded.getBitmap().get(2)).isTrue();
    assertThat(decoded.getField2()).isEqualTo("ABC");
  }

  // -- extension bit validation --

  @Test
  void extension_bit_rejected() {
    BitmappedFieldSpec<MultiBlockMessage, String> extensionSpec =
        BitmappedFieldSpec.of(
            1,
            FieldSpec.of(
                "bad",
                Codecs.ascii(1),
                MultiBlockMessage::getField2,
                MultiBlockMessage::setField2));

    BitmappedCodecBuilder.DataFieldBuilder<MultiBlockMessage> builder =
        BitmappedCodecBuilder.builder(MultiBlockMessage::new)
            .multiBlockBitmap(8, MultiBlockMessage::getBitmap, MultiBlockMessage::setBitmap);

    assertThatThrownBy(() -> builder.dataField(extensionSpec))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("extension bit");
  }

  @Test
  void single_block_bitmap_allows_bit_1() throws IOException {
    SequentialObjectCodec<TestMessage> codecWithBit1 =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap)
            .dataField(FIELD_2)
            .build();

    // bit 2 set: 0100_0000 = 0x40
    byte[] data = concat(bitmap(0x40), "ABC".getBytes());

    TestMessage decoded = codecWithBit1.decode(new ByteArrayInputStream(data));
    assertThat(decoded.getField2()).isEqualTo("ABC");
  }

  // -- skip --

  @Test
  void skip_reads_and_discards() throws IOException {
    SequentialObjectCodec<TestMessage> codecWithSkip =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap)
            .skip(2, Codecs.ascii(3))
            .dataField(FIELD_3)
            .build();

    // bits 2+3 set: 0110_0000 = 0x60
    byte[] data = concat(bitmap(0x60), "ABC".getBytes(), "DEFGH".getBytes());

    TestMessage decoded = codecWithSkip.decode(new ByteArrayInputStream(data));
    assertThat(decoded.getField2()).isNull();
    assertThat(decoded.getField3()).isEqualTo("DEFGH");
  }

  @Test
  void skip_absent_field_not_read() throws IOException {
    SequentialObjectCodec<TestMessage> codecWithSkip =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap)
            .skip(2, Codecs.ascii(3))
            .dataField(FIELD_3)
            .build();

    // only bit 3 set: 0010_0000 = 0x20
    byte[] data = concat(bitmap(0x20), "DEFGH".getBytes());

    TestMessage decoded = codecWithSkip.decode(new ByteArrayInputStream(data));
    assertThat(decoded.getField3()).isEqualTo("DEFGH");
  }

  @Test
  void skip_clears_bitmap_on_decode() throws IOException {
    SequentialObjectCodec<TestMessage> codecWithSkip =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap)
            .skip(2, Codecs.ascii(3))
            .dataField(FIELD_3)
            .build();

    // bits 2+3 set: 0110_0000 = 0x60
    byte[] data = concat(bitmap(0x60), "ABC".getBytes(), "DEFGH".getBytes());

    TestMessage decoded = codecWithSkip.decode(new ByteArrayInputStream(data));
    assertThat(decoded.getBitmap().get(2)).isFalse();
    assertThat(decoded.getField3()).isEqualTo("DEFGH");

    // re-encode — skipped field is not written (bit was cleared)
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codecWithSkip.encode(decoded, output);
    byte[] expected = concat(bitmap(0x20), "DEFGH".getBytes());
    assertThat(output.toByteArray()).isEqualTo(expected);
  }

  @Test
  void skip_zero_bit_rejected() {
    BitmappedCodecBuilder.DataFieldBuilder<TestMessage> builder =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap);
    Codec<String> skipCodec = Codecs.ascii(1);

    assertThatThrownBy(() -> builder.skip(0, skipCodec))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bit must be positive");
  }

  @Test
  void skip_extension_bit_rejected() {
    BitmappedCodecBuilder.DataFieldBuilder<MultiBlockMessage> builder =
        BitmappedCodecBuilder.builder(MultiBlockMessage::new)
            .multiBlockBitmap(8, MultiBlockMessage::getBitmap, MultiBlockMessage::setBitmap);
    Codec<String> skipCodec = Codecs.ascii(1);

    assertThatThrownBy(() -> builder.skip(1, skipCodec))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("extension bit");
  }

  // -- reject --

  @Test
  void reject_throws_on_decode() {
    SequentialObjectCodec<TestMessage> codecWithReject =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap)
            .reject(2, "deprecated field")
            .dataField(FIELD_3)
            .build();

    // bit 2 set: 0100_0000 = 0x40
    ByteArrayInputStream input = new ByteArrayInputStream(concat(bitmap(0x40)));
    assertThatThrownBy(() -> codecWithReject.decode(input))
        .isInstanceOf(CodecException.class)
        .hasMessageContaining("codec not implemented");
  }

  @Test
  void reject_throws() {
    assertThatThrownBy(() -> BitmappedCodecBuilder.DataFieldBuilder.reject(null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("setter method for rejected field should never be called");
  }

  @Test
  void reject_absent_field_ok() throws IOException {
    SequentialObjectCodec<TestMessage> codecWithReject =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap)
            .reject(2, "deprecated field")
            .dataField(FIELD_3)
            .build();

    // only bit 3 set: 0010_0000 = 0x20
    byte[] data = concat(bitmap(0x20), "DEFGH".getBytes());

    TestMessage decoded = codecWithReject.decode(new ByteArrayInputStream(data));
    assertThat(decoded.getField3()).isEqualTo("DEFGH");
  }

  @Test
  void reject_throws_on_encode() {
    SequentialObjectCodec<TestMessage> codecWithReject =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap)
            .reject(2, "deprecated field")
            .dataField(FIELD_3)
            .build();

    TestMessage msg = new TestMessage();
    msg.getBitmap().set(2);
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    assertThatThrownBy(() -> codecWithReject.encode(msg, output))
        .isInstanceOf(CodecException.class)
        .hasMessageContaining(
            "field [reject(2)]: getter method for rejected field should never be called");
  }

  @Test
  void reject_zero_bit_rejected() {
    BitmappedCodecBuilder.DataFieldBuilder<TestMessage> builder =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap);

    assertThatThrownBy(() -> builder.reject(0, "bad"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bit must be positive");
  }

  @Test
  void reject_extension_bit_rejected() {
    BitmappedCodecBuilder.DataFieldBuilder<MultiBlockMessage> builder =
        BitmappedCodecBuilder.builder(MultiBlockMessage::new)
            .multiBlockBitmap(8, MultiBlockMessage::getBitmap, MultiBlockMessage::setBitmap);

    assertThatThrownBy(() -> builder.reject(1, "bad"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("extension bit");
  }

  // -- helper methods --

  @Test
  void skip_getter_returns_null() {
    Object value = BitmappedCodecBuilder.DataFieldBuilder.skip(new TestMessage());
    assertThat(value).isNull();
  }

  @Test
  void skip_setter_clears_bit() {
    TestMessage msg = new TestMessage();
    msg.getBitmap().set(2);
    BitmappedCodecBuilder.DataFieldBuilder.skip(2).accept(msg, null);
    assertThat(msg.getBitmap().get(2)).isFalse();
  }

  // -- out-of-order fields --

  @Test
  void data_fields_sorted_by_bit_index() throws IOException {
    // Register bit 3 before bit 2 — should still decode correctly
    SequentialObjectCodec<TestMessage> outOfOrderCodec =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap)
            .dataField(FIELD_3)
            .dataField(FIELD_2)
            .build();

    // bits 2+3 set: 0110_0000 = 0x60
    // Wire order: bitmap, field2 (3 bytes), field3 (5 bytes)
    byte[] data = concat(bitmap(0x60), "ABC".getBytes(), "DEFGH".getBytes());

    TestMessage decoded = outOfOrderCodec.decode(new ByteArrayInputStream(data));
    assertThat(decoded.getField2()).isEqualTo("ABC");
    assertThat(decoded.getField3()).isEqualTo("DEFGH");

    // Roundtrip
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    outOfOrderCodec.encode(decoded, output);
    assertThat(output.toByteArray()).isEqualTo(data);
  }

  // -- duplicate bit detection --

  @Test
  void duplicate_data_field_rejected() {
    BitmappedCodecBuilder.DataFieldBuilder<TestMessage> builder =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap);
    builder.dataField(FIELD_2);

    BitmappedFieldSpec<TestMessage, String> duplicateField2 =
        BitmappedFieldSpec.of(
            2,
            FieldSpec.of(
                "duplicate", Codecs.ascii(3), TestMessage::getField2, TestMessage::setField2));

    assertThatThrownBy(() -> builder.dataField(duplicateField2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate codec for bit 2");
  }

  @Test
  void duplicate_skip_rejected() {
    BitmappedCodecBuilder.DataFieldBuilder<TestMessage> builder =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap);
    builder.dataField(FIELD_2);

    Codec<String> skipCodec = Codecs.ascii(3);
    assertThatThrownBy(() -> builder.skip(2, skipCodec))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate codec for bit 2");
  }

  @Test
  void duplicate_reject_rejected() {
    BitmappedCodecBuilder.DataFieldBuilder<TestMessage> builder =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap);
    builder.dataField(FIELD_2);

    assertThatThrownBy(() -> builder.reject(2, "dup"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate codec for bit 2");
  }

  // -- bitmap validation --

  @Test
  void unregistered_intermediate_bit_throws_on_decode() {
    // Register codecs for bits 2 and 4, but not bit 3
    BitmappedFieldSpec<TestMessage, String> field4 =
        BitmappedFieldSpec.of(
            4,
            FieldSpec.of(
                "field4", Codecs.ascii(2), TestMessage::getField3, TestMessage::setField3));

    SequentialObjectCodec<TestMessage> sparseCodec =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap)
            .dataField(FIELD_2)
            .dataField(field4)
            .build();

    // bits 2+3+4 set: 0111_0000 = 0x70
    byte[] data = concat(bitmap(0x70), "ABC".getBytes(), "XXX".getBytes(), "YZ".getBytes());
    ByteArrayInputStream input = new ByteArrayInputStream(data);

    assertThatThrownBy(() -> sparseCodec.decode(input))
        .isInstanceOf(CodecException.class)
        .hasMessageContaining("bit 3")
        .hasMessageContaining("no codec");
  }

  @Test
  void bits_beyond_last_registered_are_ignored() throws IOException {
    // Register codec only for bit 2, bitmap has bits 2 and 5 set
    SequentialObjectCodec<TestMessage> sparseCodec =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap)
            .dataField(FIELD_2)
            .build();

    // bits 2+5 set: 0100_1000 = 0x48
    // Field 2 data (3 bytes) + field 5 data (trailing, ignored)
    byte[] data = concat(bitmap(0x48), "ABC".getBytes(), "EXTRA".getBytes());

    TestMessage decoded = sparseCodec.decode(new ByteArrayInputStream(data));
    assertThat(decoded.getField2()).isEqualTo("ABC");
  }

  @Test
  void extension_bits_excluded_from_validation() throws IOException {
    // Multi-block bitmap: bit 1 is extension bit, should not trigger validation
    SequentialObjectCodec<MultiBlockMessage> multiCodec =
        BitmappedCodecBuilder.builder(MultiBlockMessage::new)
            .multiBlockBitmap(8, MultiBlockMessage::getBitmap, MultiBlockMessage::setBitmap)
            .dataField(MULTI_FIELD_2)
            .build();

    // bits 2+65 on wire: extension bit 1 auto-set when bit 65 is decoded
    byte[] block1 = new byte[8];
    block1[0] = (byte) 0xC0; // extension (bit 1) + bit 2
    byte[] block2 = new byte[8];
    block2[0] = (byte) 0x40; // bit 65 (bit 2 of block 2)
    byte[] data = concat(block1, block2, "ABC".getBytes());

    MultiBlockMessage decoded = multiCodec.decode(new ByteArrayInputStream(data));
    assertThat(decoded.getField2()).isEqualTo("ABC");
  }

  @Test
  void no_data_fields_decodes_bitmap_only() throws IOException {
    SequentialObjectCodec<TestMessage> bitmapOnly =
        BitmappedCodecBuilder.builder(TestMessage::new)
            .singleBlockBitmap(8, TestMessage::getBitmap, TestMessage::setBitmap)
            .build();

    // bits 2+3 set: 0110_0000 = 0x60 — no data fields registered, no validation error
    TestMessage decoded = bitmapOnly.decode(new ByteArrayInputStream(bitmap(0x60)));
    assertThat(decoded.getBitmap().get(2)).isTrue();
    assertThat(decoded.getBitmap().get(3)).isTrue();
  }

  // -- test fixtures --

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
