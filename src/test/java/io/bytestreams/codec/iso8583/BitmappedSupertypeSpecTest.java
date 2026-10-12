package io.bytestreams.codec.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.bytestreams.codec.core.CodecException;
import io.bytestreams.codec.core.Codecs;
import io.bytestreams.codec.core.FieldSpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Verifies that specs declared on a supertype can be used in a subtype's bitmapped builder. */
class BitmappedSupertypeSpecTest {

  static final BitmappedFieldSpec<BaseMessage, String> BASE_FIELD_2 =
      BitmappedFieldSpec.of(
          2,
          FieldSpec.of("field2", Codecs.ascii(3), BaseMessage::getField2, BaseMessage::setField2));

  static final FieldSpec<BaseMessage, String> BASE_MTI =
      FieldSpec.of("mti", Codecs.ascii(4), BaseMessage::getMti, BaseMessage::setMti);

  static final FieldSpec<BaseMessage, SingleBlockBitmap> BASE_BITMAP =
      FieldSpec.of(
          "bitmap",
          FieldCodecs.singleBlockBitmap(8),
          BaseMessage::getBitmap,
          BaseMessage::setBitmap);

  static final BitmappedFieldSpec<SubMessage, String> SUB_FIELD_3 =
      BitmappedFieldSpec.of(
          3, FieldSpec.of("field3", Codecs.ascii(2), SubMessage::getField3, SubMessage::setField3));

  private final BitmappedCodec<SubMessage> codec =
      BitmappedCodecBuilder.builder(SubMessage::new)
          .field(BASE_MTI)
          .bitmap(BASE_BITMAP)
          .dataField(BASE_FIELD_2)
          .dataField(SUB_FIELD_3)
          .build();

  @Test
  void encode_decode_round_trip_with_base_and_sub_specs() throws IOException {
    SubMessage msg = new SubMessage();
    msg.setMti("0200");
    BASE_FIELD_2.set(msg, "abc");
    SUB_FIELD_3.set(msg, "xy");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    codec.encode(msg, out);

    SubMessage decoded = codec.decode(new ByteArrayInputStream(out.toByteArray()));

    assertThat(decoded.getMti()).isEqualTo("0200");
    assertThat(decoded.getField2()).isEqualTo("abc");
    assertThat(decoded.getField3()).isEqualTo("xy");
    assertThat(out.toByteArray()).hasSize(4 + 8 + 3 + 2);
  }

  @Test
  void base_spec_sets_bitmap_bit_on_subtype() {
    SubMessage msg = new SubMessage();
    BASE_FIELD_2.set(msg, "abc");
    assertThat(msg.getBitmap().get(2)).isTrue();
    BASE_FIELD_2.set(msg, null);
    assertThat(msg.getBitmap().get(2)).isFalse();
  }

  @Test
  void field_specs_map_exposes_base_spec() {
    assertThat(codec.fieldSpecs()).containsEntry(2, BASE_FIELD_2).containsEntry(3, SUB_FIELD_3);
  }

  @Test
  void skip_with_base_bitmap_spec_discards_value_on_decode() throws IOException {
    BitmappedCodec<SubMessage> skipping =
        BitmappedCodecBuilder.builder(SubMessage::new)
            .field(BASE_MTI)
            .bitmap(BASE_BITMAP)
            .skip(2, Codecs.ascii(3))
            .dataField(SUB_FIELD_3)
            .build();
    SubMessage msg = new SubMessage();
    msg.setMti("0200");
    BASE_FIELD_2.set(msg, "abc");
    SUB_FIELD_3.set(msg, "xy");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    codec.encode(msg, out);

    SubMessage decoded = skipping.decode(new ByteArrayInputStream(out.toByteArray()));

    assertThat(decoded.getField2()).isNull();
    assertThat(decoded.getBitmap().get(2)).isFalse();
    assertThat(decoded.getField3()).isEqualTo("xy");
  }

  @Test
  void reject_with_base_bitmap_spec_throws_when_bit_set() {
    BitmappedCodec<SubMessage> rejecting =
        BitmappedCodecBuilder.builder(SubMessage::new)
            .field(BASE_MTI)
            .bitmap(BASE_BITMAP)
            .reject(2, "field2")
            .build();
    byte[] wire = "0200".getBytes(StandardCharsets.US_ASCII);
    byte[] bitmap = {0x40, 0, 0, 0, 0, 0, 0, 0};
    byte[] all = new byte[wire.length + bitmap.length];
    System.arraycopy(wire, 0, all, 0, wire.length);
    System.arraycopy(bitmap, 0, all, wire.length, bitmap.length);

    ByteArrayInputStream input = new ByteArrayInputStream(all);

    assertThatThrownBy(() -> rejecting.decode(input))
        .isInstanceOf(CodecException.class)
        .hasMessageContaining("codec not implemented");
  }

  // -- message type that does not implement Bitmapped --

  static final FieldSpec<PlainMessage, SingleBlockBitmap> PLAIN_BITMAP =
      FieldSpec.of(
          "bitmap", FieldCodecs.singleBlockBitmap(8), PlainMessage::bitmap, (m, b) -> m.bitmap = b);

  @Test
  void non_bitmapped_message_skip_and_reject_use_bitmap_spec() throws IOException {
    BitmappedCodec<PlainMessage> plain =
        BitmappedCodecBuilder.builder(PlainMessage::new)
            .bitmap(PLAIN_BITMAP)
            .skip(2, Codecs.ascii(3))
            .reject(5, "field5")
            .build();
    byte[] wire = new byte[] {0x40, 0, 0, 0, 0, 0, 0, 0, 'a', 'b', 'c'};

    PlainMessage decoded = plain.decode(new ByteArrayInputStream(wire));

    assertThat(decoded.bitmap().get(2)).isFalse();

    byte[] rejected = new byte[] {0x08, 0, 0, 0, 0, 0, 0, 0};
    ByteArrayInputStream rejectedInput = new ByteArrayInputStream(rejected);
    assertThatThrownBy(() -> plain.decode(rejectedInput))
        .isInstanceOf(CodecException.class)
        .hasMessageContaining("codec not implemented");
  }

  @Test
  void non_bitmapped_message_encode_writes_bitmap() throws IOException {
    BitmappedCodec<PlainMessage> plain =
        BitmappedCodecBuilder.builder(PlainMessage::new)
            .bitmap(PLAIN_BITMAP)
            .skip(2, Codecs.ascii(3))
            .build();
    PlainMessage msg = new PlainMessage();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    plain.encode(msg, out);
    assertThat(out.toByteArray()).containsExactly(0, 0, 0, 0, 0, 0, 0, 0);
  }

  // -- fixtures --

  static class BaseMessage implements Bitmapped {
    private SingleBlockBitmap bitmap = new SingleBlockBitmap(8);
    private String mti;
    private String field2;

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
  }

  static class SubMessage extends BaseMessage {
    private String field3;

    public String getField3() {
      return field3;
    }

    public void setField3(String field3) {
      this.field3 = field3;
    }
  }

  static class PlainMessage {
    private SingleBlockBitmap bitmap = new SingleBlockBitmap(8);

    SingleBlockBitmap bitmap() {
      return bitmap;
    }
  }
}
