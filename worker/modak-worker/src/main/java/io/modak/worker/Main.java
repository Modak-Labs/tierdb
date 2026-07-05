package io.modak.worker;

import io.modak.worker.cli.IngestCommand;
import io.modak.worker.cli.MaintainCommand;
import io.modak.worker.cli.PolicyCommand;
import io.modak.worker.cli.ProfileCommand;
import io.modak.worker.cli.TableRegistrar;
import io.modak.worker.cli.TableUnregistrar;
import io.modak.worker.cli.TableVerifier;

/** Entrypoint for the worker binary. */
public final class Main {

    private Main() {}

    public static void main(String[] args) throws Exception {
        WorkerConfig config = WorkerConfig.fromEnv(System.getenv());
        String command = args.length > 0 ? args[0] : "run";
        switch (command) {
            case "run" -> {
                WorkerDaemon daemon = new WorkerDaemon(config);
                daemon.start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        daemon.stop();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }, "modak-shutdown"));
                Thread.currentThread().join();
            }
            case "register" -> TableRegistrar.run(config, args);
            case "unregister" -> TableUnregistrar.run(config, args);
            case "verify" -> System.exit(TableVerifier.run(config, args));
            case "ingest" -> IngestCommand.run(config, args);
            case "policy" -> PolicyCommand.run(config, args);
            case "maintain" -> System.exit(MaintainCommand.run(config, args));
            case "profile" -> ProfileCommand.run(config, args);
            default -> {
                System.err.println("""
                        usage: modak-worker [run]
                               modak-worker register --table <schema.table> --pk <col>[,<col>...] --tier-key <col>
                                                     [--mode tiered|mirrored] [--heap-retention <n>]
                                                     [--lake-retention <n>] [--partition-width <n>]
                                                     [--keep-heap] [--profile <name>]
                               modak-worker unregister --table <schema.table> [--drop-lake]
                               modak-worker verify --table <schema.table>
                               modak-worker ingest --table <schema.table> [--file <parquet>...] [--jsonl <file>]
                               modak-worker policy --table <schema.table> [--set <key=value>...]
                                                   [--unset <key>...] [--reset]
                               modak-worker maintain --table <schema.table> [--no-wait]
                               modak-worker profile list
                               modak-worker profile create --name <name> --warehouse <root>
                                                           [--format <plugin>] [--config <key=value;...>]
                                                           [--credentials <ref>] [--default]
                        """);
                System.exit(2);
            }
        }
    }
}
