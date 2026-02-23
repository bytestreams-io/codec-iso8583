package io.bytestreams.codec.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.bytestreams.codec.core.Codec;
import io.github.lyang.randomparamsresolver.RandomParametersExtension;
import io.github.lyang.randomparamsresolver.RandomParametersExtension.Randomize;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
}
