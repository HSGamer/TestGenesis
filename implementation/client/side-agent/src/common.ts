import { Duration } from "@bufbuild/protobuf";
import { CommandState, PlaybackState } from "@seleniumhq/side-runtime";
import type { CommandShape } from "@seleniumhq/side-model";

/**
 * Converts milliseconds to google.protobuf.Duration.
 */
export function msToDuration(ms: number): Duration {
  return new Duration({
    seconds: BigInt(Math.floor(ms / 1000)),
    nanos: (ms % 1000) * 1000000,
  });
}

// --- Side Engine Report Types ---

export type PlaybackTimestamp = {
  state: PlaybackState;
  timestamp: Date;
};

export type CommandTimestamp = {
  state: CommandState;
  timestamp: Date;
  message?: string;
  error?: Error;
};

export type TestCommandReport = {
  id: string;
  command: CommandShape;
  state: CommandState;
  timestamp: CommandTimestamp[];
};

export type TestReport = {
  id: string;
  name: string;
  state: PlaybackState;
  timestamp: PlaybackTimestamp[];
  commands: TestCommandReport[];
  logs: string[];
};
