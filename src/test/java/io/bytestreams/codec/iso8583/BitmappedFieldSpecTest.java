package io.bytestreams.codec.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.bytestreams.codec.core.Codecs;
import io.bytestreams.codec.core.DataObject;
import io.bytestreams.codec.core.FieldSpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class BitmappedFieldSpecTest {

  static final FieldSpec<TestMessage, SingleBlockBitmap> BITMAP_SPEC =
      FieldSpec.of(
          "bitmap",
          FieldCodecs.singleBlockBitmap(8),
          TestMessage::getBitmap,
          TestMessage::setBitmap);

  static class TestMessage extends DataObject implements Bitmapped {
    private SingleBlockBitmap bitmap = new SingleBlockBitmap(8);

    static final BitmappedFieldSpec<TestMessage, String> FIELD_2 =
        BitmappedFieldSpec.of(2, field("field2", Codecs.ascii(3)));
    static final BitmappedFieldSpec<TestMessage, String> FIELD_3 =
        BitmappedFieldSpec.of(3, field("field3", Codecs.ascii(5)));

    @Override
    public SingleBlockBitmap getBitmap() {
      return bitmap;
    }

    public void setBitmap(SingleBlockBitmap bitmap) {
      this.bitmap = bitmap;
    }

    public String getField2() {
      return get(FIELD_2);
    }

    public void setField2(String value) {
      set(FIELD_2, value);
    }

    public String getField3() {
      return get(FIELD_3);
    }

    public void setField3(String value) {
      set(FIELD_3, value);
    }
  }

  @Test
  void bit() {
    assertThat(TestMessage.FIELD_2.bit()).isEqualTo(2);
    assertThat(TestMessage.FIELD_3.bit()).isEqualTo(3);
  }

  @Test
  void name() {
    assertThat(TestMessage.FIELD_2.name()).isEqualTo("field2");
  }

  @Test
  void codec() {
    assertThat(TestMessage.FIELD_2.codec()).isNotNull();
  }

  @Test
  void set_updates_bitmap() {
    TestMessage msg = new TestMessage();
    msg.setField2("abc");
    assertThat(msg.getBitmap().get(2)).isTrue();
    assertThat(msg.getField2()).isEqualTo("abc");
  }

  @Test
  void set_null_clears_bitmap() {
    TestMessage msg = new TestMessage();
    msg.setField2("abc");
    assertThat(msg.getBitmap().get(2)).isTrue();
    msg.setField2(null);
    assertThat(msg.getBitmap().get(2)).isFalse();
    assertThat(msg.getField2()).isNull();
  }

  @Test
  void presence_checks_bitmap() {
    TestMessage msg = new TestMessage();
    assertThat(TestMessage.FIELD_2.presence().test(msg)).isFalse();
    msg.setField2("abc");
    assertThat(TestMessage.FIELD_2.presence().test(msg)).isTrue();
  }

  @Test
  void codec_roundtrip_with_builder() throws IOException {
    var codec =
        BitmappedCodecBuilder.<TestMessage>builder(TestMessage::new)
            .bitmap(BITMAP_SPEC)
            .dataField(TestMessage.FIELD_2)
            .dataField(TestMessage.FIELD_3)
            .build();

    TestMessage original = new TestMessage();
    original.setField2("abc");
    original.setField3("hello");

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(original, output);

    TestMessage decoded = codec.decode(new ByteArrayInputStream(output.toByteArray()));
    assertThat(decoded.getField2()).isEqualTo("abc");
    assertThat(decoded.getField3()).isEqualTo("hello");
    assertThat(decoded.getBitmap().get(2)).isTrue();
    assertThat(decoded.getBitmap().get(3)).isTrue();
  }

  @Test
  void codec_roundtrip_partial_fields() throws IOException {
    var codec =
        BitmappedCodecBuilder.<TestMessage>builder(TestMessage::new)
            .bitmap(BITMAP_SPEC)
            .dataField(TestMessage.FIELD_2)
            .dataField(TestMessage.FIELD_3)
            .build();

    TestMessage original = new TestMessage();
    original.setField2("abc");
    // field3 not set

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(original, output);

    TestMessage decoded = codec.decode(new ByteArrayInputStream(output.toByteArray()));
    assertThat(decoded.getField2()).isEqualTo("abc");
    assertThat(decoded.getField3()).isNull();
    assertThat(decoded.getBitmap().get(2)).isTrue();
    assertThat(decoded.getBitmap().get(3)).isFalse();
  }

  @Test
  void zero_bit_rejected() {
    FieldSpec<TestMessage, String> spec = DataObject.field("f", Codecs.ascii(3));
    assertThatThrownBy(() -> BitmappedFieldSpec.of(0, spec))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bit must be positive");
  }

  @Test
  void negative_bit_rejected() {
    FieldSpec<TestMessage, String> spec = DataObject.field("f", Codecs.ascii(3));
    assertThatThrownBy(() -> BitmappedFieldSpec.of(-1, spec))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bit must be positive");
  }

  @Test
  void null_delegate_rejected() {
    assertThatThrownBy(() -> BitmappedFieldSpec.of(2, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("delegate");
  }
}
