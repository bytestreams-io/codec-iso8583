# codec-iso8583

[![Build](https://github.com/bytestreams-io/codec-iso8583/actions/workflows/build.yaml/badge.svg)](https://github.com/bytestreams-io/codec-iso8583/actions/workflows/build.yaml)
[![Dependabot Updates](https://github.com/bytestreams-io/codec-iso8583/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/bytestreams-io/codec-iso8583/actions/workflows/dependabot/dependabot-updates)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=io.bytestreams.codec%3Aiso8583&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=io.bytestreams.codec%3Aiso8583)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=io.bytestreams.codec%3Aiso8583&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=io.bytestreams.codec%3Aiso8583)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=io.bytestreams.codec%3Aiso8583&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=io.bytestreams.codec%3Aiso8583)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=io.bytestreams.codec%3Aiso8583&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=io.bytestreams.codec%3Aiso8583)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=io.bytestreams.codec%3Aiso8583&metric=bugs)](https://sonarcloud.io/summary/new_code?id=io.bytestreams.codec%3Aiso8583)
[![codecov](https://codecov.io/gh/bytestreams-io/codec-iso8583/graph/badge.svg)](https://codecov.io/gh/bytestreams-io/codec-iso8583)
[![GitHub License](https://img.shields.io/github/license/bytestreams-io/codec-iso8583)](LICENSE)
[![Javadoc](https://img.shields.io/badge/javadoc-latest-blue)](https://bytestreams-io.github.io/codec-iso8583/)

Composable codecs for ISO 8583 financial messages, built on [codec-core](https://github.com/bytestreams-io/codec-core).

## Installation

**Maven**

```xml
<dependency>
  <groupId>io.bytestreams.codec</groupId>
  <artifactId>iso8583</artifactId>
  <version>${version}</version>
</dependency>
```

**Gradle**

```groovy
implementation 'io.bytestreams.codec:iso8583:${version}'
```

## What is ISO 8583?

ISO 8583 is the international standard for financial transaction messages — the wire format behind ATM withdrawals, point-of-sale purchases, and card network authorizations. A message consists of fixed header fields (like the message type indicator), a bitmap indicating which optional data fields are present, and the data fields themselves. Some data fields contain nested structures such as BER-TLV encoded EMV chip card data.

This library provides codecs for each of these layers, from simple fixed-length fields up to full bitmap-driven messages.

## Field Codecs

ISO 8583 data fields range from simple fixed-length strings to deeply nested TLV structures. The codec-core library provides general-purpose combinators for building field codecs; codec-iso8583 adds ISO 8583–specific codecs on top. The examples below build from simple to complex.

### Fixed-length fields

Many ISO 8583 fields have a fixed length defined by the spec. codec-core's `Codecs.ascii(int)` creates a codec that reads and writes exactly that many ASCII bytes.

```java
import io.bytestreams.codec.core.Codecs;

Codec<String> mti = Codecs.ascii(4);            // Message Type Indicator, e.g. "0200"
Codec<String> amount = Codecs.ascii(12);         // Amount, e.g. "000000001500"
Codec<String> currencyCode = Codecs.ascii(3);    // Currency Code, e.g. "840"
```

### Variable-length fields (LLVAR / LLLVAR)

Some fields vary in length from message to message. ISO 8583 handles this with a length prefix convention: **LLVAR** prepends a 2-digit ASCII length, and **LLLVAR** prepends a 3-digit ASCII length, before the field content.

The `Codecs.prefixed` combinator pairs a length codec with a content codec. It encodes the length on write and reads exactly that many bytes on decode.

```java
// LLVAR — 2-digit ASCII length prefix, then variable-length ASCII content
Codec<String> pan = Codecs.prefixed(Codecs.asciiInt(2), Codecs.ascii());

// LLLVAR — 3-digit ASCII length prefix
Codec<String> additionalData = Codecs.prefixed(Codecs.asciiInt(3), Codecs.ascii());
```

### Composite fields with subfields

Some fields have internal structure composed of fixed-width subfields. For example, the Processing Code (field 3) is a 6-digit field split into three 2-digit parts: transaction type, from-account type, and to-account type.

`Codecs.sequential()` builds a codec that reads subfields in order and maps them to a POJO:

```java
Codec<ProcessingCode> processingCodeCodec = Codecs.<ProcessingCode>sequential(ProcessingCode::new)
    .field("transactionType", Codecs.ascii(2), ProcessingCode::getTransactionType, ProcessingCode::setTransactionType)
    .field("fromAccountType", Codecs.ascii(2), ProcessingCode::getFromAccountType, ProcessingCode::setFromAccountType)
    .field("toAccountType", Codecs.ascii(2), ProcessingCode::getToAccountType, ProcessingCode::setToAccountType)
    .build();
```

`ProcessingCode` is a user-defined POJO — it is shown here as an example, not a class provided by the library.

### Deeply nested fields (BER-TLV)

EMV chip card data (typically carried in ISO 8583 field 55) uses **BER-TLV** encoding, where each element is a Tag-Length-Value triplet: the tag identifies the data element, the length specifies the value size in bytes, and the value holds the raw data.

codec-iso8583 provides two codecs for BER-TLV:

- `FieldCodecs.tlvTag()` — reads/writes variable-length tags as uppercase hex strings (e.g., `"9F26"`)
- `FieldCodecs.tlvLength()` — reads/writes lengths using BER short form (0–127) and long form (128+)

These compose with codec-core combinators to build a full TLV list codec:

```java
import io.bytestreams.codec.core.Codecs;
import io.bytestreams.codec.iso8583.FieldCodecs;

Codec<String> tagCodec = FieldCodecs.tlvTag();
Codec<Integer> lengthCodec = FieldCodecs.tlvLength();

Codec<TlvElement> tlvCodec = Codecs.<TlvElement>sequential(TlvElement::new)
    .field("tag", tagCodec, TlvElement::getTag, TlvElement::setTag)
    .field("value", Codecs.prefixed(lengthCodec, Codecs.binary()), TlvElement::getValue, TlvElement::setValue)
    .build();

Codec<List<TlvElement>> tlvListCodec = Codecs.listOf(tlvCodec);
```

`TlvElement` is a user-defined POJO, not a library class. `Codecs.binary()` is a stream codec that reads until EOF — here it is bounded by `prefixed`, which limits it to the number of bytes specified by the TLV length.

## Bitmaps

A bitmap is the table of contents for an ISO 8583 message — a bit vector where each bit indicates whether a corresponding data field is present. The message sender sets bits for the fields it includes, and the receiver reads the bitmap first to know which fields follow and in what order.

### MultiBlockBitmap

`MultiBlockBitmap` is an auto-extending bitmap made up of 8-byte blocks (64 bits each). Bit indexing is 1-based, with bit 1 as the most significant bit of byte 0. Bit 1 of each block is reserved as the **extension indicator**: when set, it signals that another block follows. The library manages extension indicators automatically — you never set or clear them yourself.

```java
import io.bytestreams.codec.iso8583.MultiBlockBitmap;

MultiBlockBitmap bitmap = new MultiBlockBitmap(8);  // 8 bytes per block
bitmap.set(2);    // PAN present
bitmap.set(3);    // Processing Code present
bitmap.set(4);    // Amount present
bitmap.get(1);    // false — extension indicator, no second block needed

bitmap.set(65);   // field in second block — auto-expands
bitmap.get(1);    // true — extension indicator auto-set
bitmap.toByteArray();  // 16 bytes (2 blocks)
```

To encode and decode a bitmap on the wire, use `FieldCodecs.multiBlockBitmap(int)`. It returns a `Codec<MultiBlockBitmap>` that reads blocks dynamically — it checks each block's extension indicator to decide whether to read another block.

```java
import io.bytestreams.codec.iso8583.FieldCodecs;

Codec<MultiBlockBitmap> bitmapCodec = FieldCodecs.multiBlockBitmap(8);
```

## Putting It Together

`BitmappedCodecBuilder` composes field codecs and a bitmap codec into a full ISO 8583 message codec. Start by defining a message class that implements `Bitmapped`:

```java
import io.bytestreams.codec.iso8583.Bitmap;
import io.bytestreams.codec.iso8583.Bitmapped;
import io.bytestreams.codec.iso8583.MultiBlockBitmap;

public class AuthorizationMessage implements Bitmapped {
    private String mti;
    private MultiBlockBitmap bitmap;
    private String pan;
    private ProcessingCode processingCode;
    private String amount;

    public AuthorizationMessage() {
        this.bitmap = new MultiBlockBitmap(8);
    }

    @Override
    public Bitmap getBitmap() { return bitmap; }

    public MultiBlockBitmap getMultiBlockBitmap() { return bitmap; }
    public void setMultiBlockBitmap(MultiBlockBitmap bitmap) { this.bitmap = bitmap; }

    // other getters and setters omitted for brevity
}
```

Then build the codec with `BitmappedCodecBuilder`:

```java
import io.bytestreams.codec.core.Codecs;
import io.bytestreams.codec.iso8583.BitmappedCodecBuilder;

Codec<AuthorizationMessage> codec = BitmappedCodecBuilder.<AuthorizationMessage>builder(AuthorizationMessage::new)
    .field("mti", Codecs.ascii(4), AuthorizationMessage::getMti, AuthorizationMessage::setMti)
    .multiBlockBitmap(8, AuthorizationMessage::getMultiBlockBitmap, AuthorizationMessage::setMultiBlockBitmap)
    .dataField("pan", Codecs.prefixed(Codecs.asciiInt(2), Codecs.ascii()), AuthorizationMessage::getPan, AuthorizationMessage::setPan)
    .dataField("processingCode", processingCodeCodec, AuthorizationMessage::getProcessingCode, AuthorizationMessage::setProcessingCode)
    .dataField("amount", Codecs.ascii(12), AuthorizationMessage::getAmount, AuthorizationMessage::setAmount)
    .build();
```

The builder has two phases:

- **Phase 1** adds header fields and the bitmap with `.field()` and `.multiBlockBitmap()`. Calling `.multiBlockBitmap()` transitions to phase 2.
- **Phase 2** adds bitmap-gated data fields with `.dataField()`. Each call corresponds to the next bit position — bit 2 for PAN, bit 3 for processing code, bit 4 for amount. Bit 1 is the extension indicator and is automatically skipped. Use `.skip("field name")` to leave a bit position unimplemented.

Encode and decode a message:

```java
// Encode
ByteArrayOutputStream out = new ByteArrayOutputStream();
codec.encode(message, out);
byte[] bytes = out.toByteArray();

// Decode
AuthorizationMessage decoded = codec.decode(new ByteArrayInputStream(bytes));
```

## Requirements

- Java 17+
- [codec-core](https://github.com/bytestreams-io/codec-core)

## License

[Apache 2.0](LICENSE)
