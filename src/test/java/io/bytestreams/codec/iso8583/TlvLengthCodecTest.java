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
class TlvLengthCodecTest {
  private final Codec<Integer> codec = FieldCodecs.tlvLength();

  @Test
  void decode_short_form_zero() throws IOException {
    ByteArrayInputStream input = new ByteArrayInputStream(new byte[] {0x00});
    assertThat(codec.decode(input)).isEqualTo(0);
  }

  @Test
  void decode_short_form_max() throws IOException {
    ByteArrayInputStream input = new ByteArrayInputStream(new byte[] {0x7F});
    assertThat(codec.decode(input)).isEqualTo(127);
  }

  @Test
  void decode_one_byte_long_form() throws IOException {
    ByteArrayInputStream input = new ByteArrayInputStream(new byte[] {(byte) 0x81, (byte) 0x80});
    assertThat(codec.decode(input)).isEqualTo(128);
  }

  @Test
  void decode_two_byte_long_form() throws IOException {
    ByteArrayInputStream input = new ByteArrayInputStream(new byte[] {(byte) 0x82, 0x01, 0x00});
    assertThat(codec.decode(input)).isEqualTo(256);
  }

  @Test
  void decode_three_byte_long_form() throws IOException {
    ByteArrayInputStream input =
        new ByteArrayInputStream(new byte[] {(byte) 0x83, 0x01, 0x00, 0x00});
    assertThat(codec.decode(input)).isEqualTo(65536);
  }

  @Test
  void decode_four_byte_long_form() throws IOException {
    ByteArrayInputStream input =
        new ByteArrayInputStream(
            new byte[] {(byte) 0x84, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
    assertThat(codec.decode(input)).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void encode_short_form() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(0, output);
    assertThat(output.toByteArray()).isEqualTo(new byte[] {0x00});
  }

  @Test
  void encode_short_form_max() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(127, output);
    assertThat(output.toByteArray()).isEqualTo(new byte[] {0x7F});
  }

  @Test
  void encode_one_byte_long_form() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(128, output);
    assertThat(output.toByteArray()).isEqualTo(new byte[] {(byte) 0x81, (byte) 0x80});
  }

  @Test
  void encode_two_byte_long_form() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(256, output);
    assertThat(output.toByteArray()).isEqualTo(new byte[] {(byte) 0x82, 0x01, 0x00});
  }

  @Test
  void encode_three_byte_long_form() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(65536, output);
    assertThat(output.toByteArray()).isEqualTo(new byte[] {(byte) 0x83, 0x01, 0x00, 0x00});
  }

  @Test
  void encode_four_byte_long_form() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(Integer.MAX_VALUE, output);
    assertThat(output.toByteArray())
        .isEqualTo(new byte[] {(byte) 0x84, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
  }

  @Test
  void encode_result() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    assertThat(codec.encode(0, output))
        .satisfies(
            r -> {
              assertThat(r.count()).isEqualTo(1);
              assertThat(r.bytes()).isEqualTo(1);
            });

    output.reset();
    assertThat(codec.encode(128, output))
        .satisfies(
            r -> {
              assertThat(r.count()).isEqualTo(1);
              assertThat(r.bytes()).isEqualTo(2);
            });
  }

  @Test
  void round_trip_short_form(@Randomize(intMin = 0, intMax = 127) int length) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(length, output);
    ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
    assertThat(codec.decode(input)).isEqualTo(length);
  }

  @Test
  void round_trip_one_byte_long_form(@Randomize(intMin = 128, intMax = 255) int length)
      throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(length, output);
    ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
    assertThat(codec.decode(input)).isEqualTo(length);
  }

  @Test
  void round_trip_two_byte_long_form(@Randomize(intMin = 256, intMax = 65535) int length)
      throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(length, output);
    ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
    assertThat(codec.decode(input)).isEqualTo(length);
  }

  @Test
  void round_trip_three_byte_long_form(@Randomize(intMin = 65536, intMax = 16777215) int length)
      throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(length, output);
    ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
    assertThat(codec.decode(input)).isEqualTo(length);
  }

  @Test
  void round_trip_four_byte_long_form(
      @Randomize(intMin = 16777216, intMax = Integer.MAX_VALUE) int length) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(length, output);
    ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
    assertThat(codec.decode(input)).isEqualTo(length);
  }

  @Test
  void decode_eof_on_first_byte() {
    ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
    assertThatThrownBy(() -> codec.decode(input))
        .isInstanceOf(EOFException.class)
        .hasMessage("End of stream reached after reading %d bytes, bytes expected [%d]", 0, 1);
  }

  @Test
  void decode_eof_on_subsequent_bytes() {
    ByteArrayInputStream input = new ByteArrayInputStream(new byte[] {(byte) 0x82, 0x01});
    assertThatThrownBy(() -> codec.decode(input))
        .isInstanceOf(EOFException.class)
        .hasMessage("End of stream reached after reading %d bytes, bytes expected [%d]", 1, 2);
  }

  @Test
  void decode_indefinite_length() {
    ByteArrayInputStream input = new ByteArrayInputStream(new byte[] {(byte) 0x80});
    assertThatThrownBy(() -> codec.decode(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("indefinite length not supported");
  }

  @Test
  void decode_length_field_too_large() {
    ByteArrayInputStream input =
        new ByteArrayInputStream(new byte[] {(byte) 0x85, 0x01, 0x02, 0x03, 0x04, 0x05});
    assertThatThrownBy(() -> codec.decode(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("length field too large: %d bytes", 5);
  }

  @Test
  void decode_length_overflow() {
    ByteArrayInputStream input =
        new ByteArrayInputStream(new byte[] {(byte) 0x84, (byte) 0x80, 0x00, 0x00, 0x00});
    assertThatThrownBy(() -> codec.decode(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("length overflow: %d bytes", 4);
  }

  @Test
  void encode_negative_length() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    assertThatThrownBy(() -> codec.encode(-1, output))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("length must not be negative: %d", -1);
  }
}
