import { create } from "@bufbuild/protobuf";
import { DurationSchema, TimestampSchema } from "@bufbuild/protobuf/wkt";

/**
 * Converts milliseconds to google.protobuf.DurationSchema.
 */
export function msToDuration(ms: number) {
  return create(DurationSchema, {
    seconds: BigInt(Math.floor(ms / 1000)),
    nanos: (ms % 1000) * 1000000,
  });
}

/**
 * Creates a google.protobuf.TimestampSchema from the current time.
 */
export function timestampNow() {
  return timestampFromDate(new Date());
}

/**
 * Converts a Date to google.protobuf.TimestampSchema.
 */
export function timestampFromDate(date: Date) {
  const ms = date.getTime();
  return create(TimestampSchema, {
    seconds: BigInt(Math.floor(ms / 1000)),
    nanos: (ms % 1000) * 1000000,
  });
}
