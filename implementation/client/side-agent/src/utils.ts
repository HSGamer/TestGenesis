import { Duration } from "@bufbuild/protobuf";

/**
 * Converts milliseconds to google.protobuf.Duration.
 * 
 * @param ms - The duration in milliseconds.
 * @returns A google.protobuf.Duration object.
 */
export function msToDuration(ms: number): Duration {
  return new Duration({
    seconds: BigInt(Math.floor(ms / 1000)),
    nanos: (ms % 1000) * 1000000,
  });
}
