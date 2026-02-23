package io.bytestreams.codec.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.bytestreams.codec.core.Codec;
import io.github.lyang.randomparamsresolver.RandomParametersExtension;
import io.github.lyang.randomparamsresolver.RandomParametersExtension.Randomize;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(RandomParametersExtension.class)
class TlvTagCodecTest {
  private final Codec<String> codec = FieldCodecs.tlvTag();

  @Test
  void decode_single_byte_tag() throws IOException {
    ByteArrayInputStream input = new ByteArrayInputStream(new byte[] {(byte) 0x82});
    assertThat(codec.decode(input)).isEqualTo("82");
  }

  @Test
  void decode_multi_byte_tag() throws IOException {
    ByteArrayInputStream input = new ByteArrayInputStream(new byte[] {(byte) 0x9F, 0x02});
    assertThat(codec.decode(input)).isEqualTo("9F02");
  }

  @Test
  void decode_three_byte_tag() throws IOException {
    ByteArrayInputStream input =
        new ByteArrayInputStream(new byte[] {(byte) 0xDF, (byte) 0x81, 0x01});
    assertThat(codec.decode(input)).isEqualTo("DF8101");
  }

  @Test
  void encode_single_byte_tag() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("82", output);
    assertThat(output.toByteArray()).isEqualTo(new byte[] {(byte) 0x82});
  }

  @Test
  void encode_multi_byte_tag() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("9F02", output);
    assertThat(output.toByteArray()).isEqualTo(new byte[] {(byte) 0x9F, 0x02});
  }

  @Test
  void encode_result() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    assertThat(codec.encode("9F02", output))
        .satisfies(
            r -> {
              assertThat(r.count()).isEqualTo(1);
              assertThat(r.bytes()).isEqualTo(2);
            });
  }

  @Test
  void round_trip_single_byte(@Randomize(intMin = 0, intMax = 255) int value) throws IOException {
    byte b = (byte) value;
    if ((b & 0x1F) == 0x1F) {
      b = (byte) (b & ~0x01);
    }
    ByteArrayInputStream input = new ByteArrayInputStream(new byte[] {b});
    String tag = codec.decode(input);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(tag, output);
    assertThat(output.toByteArray()).isEqualTo(new byte[] {b});
  }

  @Test
  void round_trip_multi_byte(
      @Randomize(intMin = 0, intMax = 255) int firstValue,
      @Randomize(intMin = 0, intMax = 127) int secondValue)
      throws IOException {
    byte first = (byte) ((firstValue & 0xE0) | 0x1F);
    byte second = (byte) secondValue;
    ByteArrayInputStream input = new ByteArrayInputStream(new byte[] {first, second});
    String tag = codec.decode(input);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(tag, output);
    assertThat(output.toByteArray()).isEqualTo(new byte[] {first, second});
  }

  @Test
  void decode_eof_on_first_byte() {
    ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
    assertThatThrownBy(() -> codec.decode(input))
        .isInstanceOf(EOFException.class)
        .hasMessage("End of stream reached after reading %d bytes, bytes expected [%d]", 0, 1);
  }

  @Test
  void decode_eof_on_continuation() {
    ByteArrayInputStream input = new ByteArrayInputStream(new byte[] {(byte) 0x9F});
    assertThatThrownBy(() -> codec.decode(input))
        .isInstanceOf(EOFException.class)
        .hasMessage("End of stream reached after reading %d bytes, bytes expected [%d]", 0, 1);
  }

  @Test
  void encode_accepts_lowercase() throws IOException {
    ByteArrayOutputStream lower = new ByteArrayOutputStream();
    ByteArrayOutputStream upper = new ByteArrayOutputStream();
    codec.encode("9f02", lower);
    codec.encode("9F02", upper);
    assertThat(lower.toByteArray()).isEqualTo(upper.toByteArray());
  }
}
