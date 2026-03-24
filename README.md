# codec-iso8583

[![Build](https://github.com/bytestreams-io/codec-iso8583/actions/workflows/build.yaml/badge.svg)](https://github.com/bytestreams-io/codec-iso8583/actions/workflows/build.yaml)
[![CodeQL](https://github.com/bytestreams-io/codec-iso8583/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/bytestreams-io/codec-iso8583/actions/workflows/github-code-scanning/codeql)
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

Composite field codecs compose naturally with `BitmappedCodecBuilder`. When a data field's codec is itself a `SequentialObjectCodec`, `inspect()` recurses into it automatically:

```java
// Define the subfield codec
static final BitmappedFieldSpec<AuthorizationMessage, ProcessingCode> PROCESSING_CODE =
    BitmappedFieldSpec.of(3, field("processingCode", processingCodeCodec));

// Use it in the builder — same as any other data field
.dataField(PROCESSING_CODE)

// inspect() produces nested structure automatically:
// {"processingCode": {"transactionType": "00", "fromAccountType": "10", "toAccountType": "20"}, ...}
```

The same pattern works for any level of nesting — a subfield codec can itself contain nested codecs, and `inspect()` recurses through all of them.

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

`BitmappedCodecBuilder` composes field codecs and a bitmap codec into a full ISO 8583 message codec. Fields are defined as `FieldSpec` and `BitmappedFieldSpec` constants, which pair a codec with its accessor logic.

### Defining a message class

Extend `DataObject` and implement `Bitmapped`. Each data field is declared as a `BitmappedFieldSpec` constant that binds a bit index to a `FieldSpec`. The `DataObject` base class provides map-backed storage, so you call `get(SPEC)` and `set(SPEC, value)` instead of declaring individual fields. Setting a value automatically sets the corresponding bitmap bit; setting null clears it.

```java
import io.bytestreams.codec.core.DataObject;
import io.bytestreams.codec.iso8583.Bitmapped;
import io.bytestreams.codec.iso8583.BitmappedFieldSpec;
import io.bytestreams.codec.iso8583.MultiBlockBitmap;

public class AuthorizationMessage extends DataObject implements Bitmapped {

    static final FieldSpec<AuthorizationMessage, MultiBlockBitmap> BITMAP =
        field("bitmap", FieldCodecs.multiBlockBitmap(8));
    static final FieldSpec<AuthorizationMessage, String> MTI = field("mti", Codecs.ascii(4));

    static final BitmappedFieldSpec<AuthorizationMessage, String> PAN =
        BitmappedFieldSpec.of(2, field("pan", Codecs.prefixed(Codecs.asciiInt(2), Codecs.ascii())));
    static final BitmappedFieldSpec<AuthorizationMessage, ProcessingCode> PROCESSING_CODE =
        BitmappedFieldSpec.of(3, field("processingCode", processingCodeCodec));
    static final BitmappedFieldSpec<AuthorizationMessage, String> AMOUNT =
        BitmappedFieldSpec.of(4, field("amount", Codecs.ascii(12)));

    public AuthorizationMessage() {
        set(BITMAP, new MultiBlockBitmap(8));
    }

    @Override
    public MultiBlockBitmap getBitmap() { return get(BITMAP); }

    public String getMti() { return get(MTI); }
    public void setMti(String mti) { set(MTI, mti); }

    public String getPan() { return get(PAN); }
    public void setPan(String pan) { set(PAN, pan); }

    // other getters and setters follow the same pattern
}
```

`DataObject` and `FieldSpec` are provided by codec-core. `ProcessingCode` and `processingCodeCodec` are user-defined (see [Composite fields with subfields](#composite-fields-with-subfields) above).

### Building the codec

Use `BitmappedCodecBuilder` with the field spec constants:

```java
import io.bytestreams.codec.iso8583.BitmappedCodecBuilder;
import io.bytestreams.codec.iso8583.BitmappedCodec;

BitmappedCodec<AuthorizationMessage> codec = BitmappedCodecBuilder.<AuthorizationMessage>builder(AuthorizationMessage::new)
    .field(AuthorizationMessage.MTI)
    .bitmap(AuthorizationMessage.BITMAP)
    .dataField(AuthorizationMessage.PAN)
    .dataField(AuthorizationMessage.PROCESSING_CODE)
    .dataField(AuthorizationMessage.AMOUNT)
    .build();
```

The builder has two phases:

- **Phase 1** adds header fields and the bitmap with `.field()` and `.bitmap()`. The bitmap type (`SingleBlockBitmap` or `MultiBlockBitmap`) determines extension bit behavior automatically. Calling `.bitmap()` transitions to phase 2.
- **Phase 2** adds bitmap-gated data fields with `.dataField()`. Each `BitmappedFieldSpec` carries its own bit index — fields can be added in any order and are sorted by bit index at build time. Duplicate bit indices are rejected. For multi-block bitmaps, bit 1 of each block is reserved as the extension indicator — attempting to use it as a data field throws an exception. During decode, if the bitmap contains a set bit that has no registered codec (between the first and last registered bit), the decoder throws immediately to prevent silent data corruption.

Two additional methods handle fields you don't want to map to your message class:

- `.skip(bit, codec)` — reads and discards a field during decode; clears the bit so the field is omitted on re-encode.
- `.reject(bit, name)` — throws if the bit is set during either encode or decode, useful for deprecated or forbidden fields.

### Encode and decode

```java
// Encode
ByteArrayOutputStream out = new ByteArrayOutputStream();
codec.encode(message, out);
byte[] bytes = out.toByteArray();

// Decode
AuthorizationMessage decoded = codec.decode(new ByteArrayInputStream(bytes));
```

### Field introspection

`BitmappedCodec` implements `Inspectable<T>` from codec-core. Call `inspect()` to extract all present field values as a `Map<String, Object>`, recursing into child codecs that also implement `Inspectable`:

```java
Map<String, Object> fields = (Map<String, Object>) codec.inspect(decoded);
// {"pan": "4111111111111111", "processingCode": {"transactionType": "00", ...}, "amount": "000000001500"}
```

This integrates with structured logging, OTEL span attributes, and JSON serialization. Custom codecs can implement `Inspectable` to control what `inspect()` returns (e.g., masking PANs).

## Requirements

- Java 17+ (runtime)
- Java 21+ (build)
- [codec-core](https://github.com/bytestreams-io/codec-core)

## License

[Apache 2.0](LICENSE)
