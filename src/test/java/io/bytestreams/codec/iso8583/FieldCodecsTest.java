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
class FieldCodecsTest {

  @Test
  void single_block_bitmap_validates_size() {
    assertThatThrownBy(() -> FieldCodecs.singleBlockBitmap(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void single_block_bitmap_encode(@Randomize byte[] content) throws IOException {
    SingleBlockBitmap bitmap = SingleBlockBitmap.valueOf(content);
    Codec<SingleBlockBitmap> codec = FieldCodecs.singleBlockBitmap(content.length);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(bitmap, output);
    assertThat(output.toByteArray()).isEqualTo(content);
  }

  @Test
  void single_block_bitmap_decode(@Randomize byte[] content) throws IOException {
    ByteArrayInputStream input = new ByteArrayInputStream(content);
    Codec<SingleBlockBitmap> codec = FieldCodecs.singleBlockBitmap(content.length);
    SingleBlockBitmap bitmap = codec.decode(input);
    assertThat(bitmap.toByteArray()).isEqualTo(content);
  }

  @Test
  void multi_block_bitmap_validates_size() {
    assertThatThrownBy(() -> FieldCodecs.multiBlockBitmap(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void multi_block_bitmap_encode() throws IOException {
    MultiBlockBitmap bitmap = new MultiBlockBitmap(1, 3);
    bitmap.set(10);
    Codec<MultiBlockBitmap> codec = FieldCodecs.multiBlockBitmap(1);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(bitmap, output);
    assertThat(output.toByteArray()).isEqualTo(new byte[] {(byte) 0x80, 0x40});
  }

  @Test
  void multi_block_bitmap_decode(@Randomize byte[] content) throws IOException {
    content[0] = (byte) (content[0] & 0x7F); // clear extension bit
    ByteArrayInputStream input = new ByteArrayInputStream(content);
    Codec<MultiBlockBitmap> codec = FieldCodecs.multiBlockBitmap(content.length);
    MultiBlockBitmap bitmap = codec.decode(input);
    assertThat(bitmap.toByteArray()).isEqualTo(content);
  }

  @Test
  void multi_block_bitmap_decode_with_extension(@Randomize(length = 10) byte[] content)
      throws IOException {
    content[0] = (byte) (content[0] | 0x80); // set extension bit on block 0
    content[content.length / 2] = (byte) (content[content.length / 2] & 0x7F); // clear on block 1
    ByteArrayInputStream input = new ByteArrayInputStream(content);
    Codec<MultiBlockBitmap> codec = FieldCodecs.multiBlockBitmap(content.length / 2);
    MultiBlockBitmap bitmap = codec.decode(input);
    assertThat(bitmap.toByteArray()).isEqualTo(content);
  }

  @Test
  void multi_block_bitmap_round_trip(@Randomize(length = 10) byte[] content) throws IOException {
    content[0] = (byte) (content[0] | 0x80);
    content[content.length / 2] = (byte) (content[content.length / 2] & 0x7F);
    Codec<MultiBlockBitmap> codec = FieldCodecs.multiBlockBitmap(content.length / 2);
    MultiBlockBitmap bitmap = codec.decode(new ByteArrayInputStream(content));
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(bitmap, output);
    assertThat(output.toByteArray()).isEqualTo(content);
  }

  @Test
  void multi_block_bitmap_decode_insufficient_data(@Randomize byte[] content) {
    ByteArrayInputStream input = new ByteArrayInputStream(content);
    Codec<MultiBlockBitmap> codec = FieldCodecs.multiBlockBitmap(content.length + 1);
    assertThatThrownBy(() -> codec.decode(input)).isInstanceOf(EOFException.class);
  }

  @Test
  void multi_block_bitmap_with_max_blocks_validates_size() {
    assertThatThrownBy(() -> FieldCodecs.multiBlockBitmap(0, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void multi_block_bitmap_with_max_blocks_round_trip(@Randomize(length = 10) byte[] content)
      throws IOException {
    content[0] = (byte) (content[0] | 0x80);
    content[content.length / 2] = (byte) (content[content.length / 2] & 0x7F);
    Codec<MultiBlockBitmap> codec = FieldCodecs.multiBlockBitmap(content.length / 2, 2);
    MultiBlockBitmap bitmap = codec.decode(new ByteArrayInputStream(content));
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    codec.encode(bitmap, output);
    assertThat(output.toByteArray()).isEqualTo(content);
  }
}
