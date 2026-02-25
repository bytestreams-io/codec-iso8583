package io.bytestreams.codec.iso8583;

import static org.assertj.core.api.Assertions.assertThat;

import io.bytestreams.codec.core.Codec;
import io.bytestreams.codec.core.Codecs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class VarLengthTest {

  // --- LLVAR (2-digit prefix) ---

  @Test
  void llvar_ascii_byte_length() throws IOException {
    Codec<String> codec = FieldCodecs.llvar(Codecs::asciiInt, Codecs.hex());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    // prefix "02" in ASCII (byte count), then 0xAA 0xBB
    assertThat(output.toByteArray()).isEqualTo(new byte[] {0x30, 0x32, (byte) 0xAA, (byte) 0xBB});
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  @Test
  void llvar_ascii_item_length() throws IOException {
    Codec<String> codec = FieldCodecs.llvar(Codecs::asciiInt, String::length, Codecs::hex);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    // prefix "04" in ASCII (hex char count), then 0xAA 0xBB
    assertThat(output.toByteArray()).isEqualTo(new byte[] {0x30, 0x34, (byte) 0xAA, (byte) 0xBB});
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  @Test
  void llvar_ebcdic_byte_length() throws IOException {
    Codec<String> codec = FieldCodecs.llvar(Codecs::ebcdicInt, Codecs.hex());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    assertThat(output.toByteArray())
        .isEqualTo(new byte[] {(byte) 0xF0, (byte) 0xF2, (byte) 0xAA, (byte) 0xBB});
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  @Test
  void llvar_ebcdic_item_length() throws IOException {
    Codec<String> codec = FieldCodecs.llvar(Codecs::ebcdicInt, String::length, Codecs::hex);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    assertThat(output.toByteArray())
        .isEqualTo(new byte[] {(byte) 0xF0, (byte) 0xF4, (byte) 0xAA, (byte) 0xBB});
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  @Test
  void llvar_bcd_byte_length() throws IOException {
    Codec<String> codec = FieldCodecs.llvar(Codecs::bcdInt, Codecs.hex());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    // prefix 02 in BCD (1 byte), then 0xAA 0xBB
    assertThat(output.toByteArray()).isEqualTo(new byte[] {0x02, (byte) 0xAA, (byte) 0xBB});
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  @Test
  void llvar_bcd_item_length() throws IOException {
    Codec<String> codec = FieldCodecs.llvar(Codecs::bcdInt, String::length, Codecs::hex);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    // prefix 04 in BCD (1 byte), then 0xAA 0xBB
    assertThat(output.toByteArray()).isEqualTo(new byte[] {0x04, (byte) 0xAA, (byte) 0xBB});
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  // --- LLLVAR (3-digit prefix) ---

  @Test
  void lllvar_ascii_byte_length() throws IOException {
    Codec<String> codec = FieldCodecs.lllvar(Codecs::asciiInt, Codecs.hex());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    assertThat(output.toByteArray())
        .isEqualTo(new byte[] {0x30, 0x30, 0x32, (byte) 0xAA, (byte) 0xBB});
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  @Test
  void lllvar_ascii_item_length() throws IOException {
    Codec<String> codec = FieldCodecs.lllvar(Codecs::asciiInt, String::length, Codecs::hex);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    assertThat(output.toByteArray())
        .isEqualTo(new byte[] {0x30, 0x30, 0x34, (byte) 0xAA, (byte) 0xBB});
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  @Test
  void lllvar_ebcdic_byte_length() throws IOException {
    Codec<String> codec = FieldCodecs.lllvar(Codecs::ebcdicInt, Codecs.hex());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    assertThat(output.toByteArray())
        .isEqualTo(new byte[] {(byte) 0xF0, (byte) 0xF0, (byte) 0xF2, (byte) 0xAA, (byte) 0xBB});
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  @Test
  void lllvar_ebcdic_item_length() throws IOException {
    Codec<String> codec = FieldCodecs.lllvar(Codecs::ebcdicInt, String::length, Codecs::hex);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    assertThat(output.toByteArray())
        .isEqualTo(new byte[] {(byte) 0xF0, (byte) 0xF0, (byte) 0xF4, (byte) 0xAA, (byte) 0xBB});
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  @Test
  void lllvar_bcd_byte_length() throws IOException {
    Codec<String> codec = FieldCodecs.lllvar(Codecs::bcdInt, Codecs.hex());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    assertThat(output.toByteArray()).isEqualTo(new byte[] {0x00, 0x02, (byte) 0xAA, (byte) 0xBB});
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  @Test
  void lllvar_bcd_item_length() throws IOException {
    Codec<String> codec = FieldCodecs.lllvar(Codecs::bcdInt, String::length, Codecs::hex);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    assertThat(output.toByteArray()).isEqualTo(new byte[] {0x00, 0x04, (byte) 0xAA, (byte) 0xBB});
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  // --- LLLLVAR (4-digit prefix) ---

  @Test
  void llllvar_ascii_byte_length() throws IOException {
    Codec<String> codec = FieldCodecs.llllvar(Codecs::asciiInt, Codecs.hex());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    assertThat(output.toByteArray())
        .isEqualTo(new byte[] {0x30, 0x30, 0x30, 0x32, (byte) 0xAA, (byte) 0xBB});
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  @Test
  void llllvar_ascii_item_length() throws IOException {
    Codec<String> codec = FieldCodecs.llllvar(Codecs::asciiInt, String::length, Codecs::hex);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    assertThat(output.toByteArray())
        .isEqualTo(new byte[] {0x30, 0x30, 0x30, 0x34, (byte) 0xAA, (byte) 0xBB});
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  @Test
  void llllvar_ebcdic_byte_length() throws IOException {
    Codec<String> codec = FieldCodecs.llllvar(Codecs::ebcdicInt, Codecs.hex());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    assertThat(output.toByteArray())
        .isEqualTo(
            new byte[] {
              (byte) 0xF0, (byte) 0xF0, (byte) 0xF0, (byte) 0xF2, (byte) 0xAA, (byte) 0xBB
            });
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  @Test
  void llllvar_ebcdic_item_length() throws IOException {
    Codec<String> codec = FieldCodecs.llllvar(Codecs::ebcdicInt, String::length, Codecs::hex);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    assertThat(output.toByteArray())
        .isEqualTo(
            new byte[] {
              (byte) 0xF0, (byte) 0xF0, (byte) 0xF0, (byte) 0xF4, (byte) 0xAA, (byte) 0xBB
            });
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  @Test
  void llllvar_bcd_byte_length() throws IOException {
    Codec<String> codec = FieldCodecs.llllvar(Codecs::bcdInt, Codecs.hex());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    assertThat(output.toByteArray()).isEqualTo(new byte[] {0x00, 0x02, (byte) 0xAA, (byte) 0xBB});
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }

  @Test
  void llllvar_bcd_item_length() throws IOException {
    Codec<String> codec = FieldCodecs.llllvar(Codecs::bcdInt, String::length, Codecs::hex);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode("AABB", output);
    assertThat(output.toByteArray()).isEqualTo(new byte[] {0x00, 0x04, (byte) 0xAA, (byte) 0xBB});
    assertThat(codec.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("AABB");
  }
}
