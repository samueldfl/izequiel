import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jocl.*;
import static org.jocl.CL.*;

public class Main {
    private static final int NUM_THREADS = 4;

    private static final String TARGET_WORD = "word";

    private static final String OUTPUT_PATH = "results.csv";

    private static final List<File> FILES = Arrays.asList(
            new File("resources/DonQuixote-388208.txt"),
            new File("resources/Dracula-165307.txt"),
            new File("resources/MobyDick-217452.txt"));

    public static void main(String[] args) {
        List<String[]> results = new ArrayList<>();

        for (File file : FILES) {
            final List<String[]> resultSerial = serialCPU(file, TARGET_WORD);
            results.addAll(resultSerial);

            final List<String[]> resultParallelCpu = paralellCPU(file, TARGET_WORD, NUM_THREADS);
            results.addAll(resultParallelCpu);

            final List<String[]> resultParallelGpu = parallelGPU(file, TARGET_WORD);
            results.addAll(resultParallelGpu);
        }

        writeResultsToCSV(OUTPUT_PATH, results);
    }

    private static List<String[]> serialCPU(final File file, final String target) {
        final List<String[]> result = new ArrayList<>();

        final long startTime = System.nanoTime();
        final long countSerial = countWordSerialCPU(file, target);
        final long endTime = System.nanoTime();
        result.add(new String[] { "SerialCPU", file.getName(), String.valueOf(countSerial),
                String.valueOf(endTime - startTime) });

        return result;
    }

    private static List<String[]> paralellCPU(final File file, String target, final int numThreads) {
        final List<String[]> result = new ArrayList<>();

        final long startTime = System.nanoTime();
        final long countWords = countWordParallelCPU(file, target, numThreads);
        final long endTime = System.nanoTime();
        result.add(new String[] { "ParallelCPU", file.getName(), String.valueOf(countWords),
                String.valueOf(endTime - startTime) });

        return result;
    }

    private static List<String[]> parallelGPU(final File file, final String target) {
        final List<String[]> result = new ArrayList<>();

        final long startTime = System.nanoTime();
        final int countParallelGPU = countWordParallelGPU(file.getPath(), target);
        final long endTime = System.nanoTime();
        result.add(new String[] { "ParallelGPU", file.getName(), String.valueOf(countParallelGPU),
                String.valueOf(endTime - startTime) });

        return result;
    }

    private static long countWordSerialCPU(final File file, final String target) {
        long count = 0;

        try (final BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String[] words = line.toLowerCase().split("\\W+");
                for (final String word : words) {
                    if (word.equals(target.toLowerCase())) {
                        count++;
                    }
                }
            }
        } catch (final IOException e) {
            System.err.println("Erro ao processar o arquivo: " + e.getMessage());
        }

        return count;
    }

    private static long countWordParallelCPU(final File file, final String target, final int numThreads) {
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        long totalOccurrences = 0;

        try (final BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            final BlockingQueue<Future<Integer>> futures = new LinkedBlockingQueue<>();

            while ((line = reader.readLine()) != null) {
                final String currentLine = line.toLowerCase();
                final Future<Integer> future = executor.submit(() -> {
                    int count = 0;
                    final String[] words = currentLine.split("\\W+");
                    for (final String word : words) {
                        if (word.equals(target.toLowerCase())) {
                            count++;
                        }
                    }
                    return count;
                });
                futures.add(future);
            }

            for (final Future<Integer> future : futures) {
                try {
                    totalOccurrences += future.get();
                } catch (InterruptedException | ExecutionException e) {
                    System.err.println("Error processing a task: " + e.getMessage());
                }
            }
        } catch (final IOException e) {
            System.err.println("An error occurred while reading the file: " + e.getMessage());
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (final InterruptedException e) {
                System.err.println("Executor interrupted during shutdown: " + e.getMessage());
                executor.shutdownNow();
            }
        }

        return totalOccurrences;
    }

    public static int countWordParallelGPU(String filePath, String targetWord) {
        setExceptionsEnabled(true);

        String text;
        try {
            text = new String(Files.readAllBytes(Paths.get(filePath)));
        } catch (IOException e) {
            System.err.println("Erro ao ler o arquivo: " + e.getMessage());
            return -1;
        }

        byte[] textBytes = text.getBytes();
        byte[] wordBytes = targetWord.getBytes();
        int[] occurrences = new int[textBytes.length];

        cl_platform_id[] platforms = new cl_platform_id[1];
        clGetPlatformIDs(platforms.length, platforms, null);

        cl_device_id[] devices = new cl_device_id[1];
        clGetDeviceIDs(platforms[0], CL_DEVICE_TYPE_GPU, devices.length, devices, null);

        cl_context context = clCreateContext(null, 1, devices, null, null, null);
        cl_command_queue commandQueue = clCreateCommandQueueWithProperties(context, devices[0], null, null);

        String kernelSource;
        try {
            kernelSource = new String(Files.readAllBytes(Paths.get("resources/kernel.cl")));
        } catch (IOException e) {
            System.err.println("Erro ao ler o arquivo do kernel: " + e.getMessage());
            return -1;
        }

        cl_program program = clCreateProgramWithSource(context, 1, new String[] { kernelSource }, null, null);
        clBuildProgram(program, 0, null, null, null, null);
        cl_kernel kernel = clCreateKernel(program, "countWord", null);

        cl_mem textBuffer = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_char * textBytes.length, Pointer.to(textBytes), null);
        cl_mem wordBuffer = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_char * wordBytes.length, Pointer.to(wordBytes), null);
        cl_mem occurrencesBuffer = clCreateBuffer(context, CL_MEM_WRITE_ONLY, Sizeof.cl_int * occurrences.length, null,
                null);

        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(textBuffer));
        clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(wordBuffer));
        clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(occurrencesBuffer));
        clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[] { textBytes.length }));
        clSetKernelArg(kernel, 4, Sizeof.cl_int, Pointer.to(new int[] { wordBytes.length }));

        long[] globalWorkSize = new long[] { textBytes.length };
        clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, globalWorkSize, null, 0, null, null);
        clEnqueueReadBuffer(commandQueue, occurrencesBuffer, CL_TRUE, 0, Sizeof.cl_int * occurrences.length,
                Pointer.to(occurrences), 0, null, null);

        int totalOccurrences = 0;
        for (int count : occurrences) {
            totalOccurrences += count;
        }

        clReleaseMemObject(textBuffer);
        clReleaseMemObject(wordBuffer);
        clReleaseMemObject(occurrencesBuffer);
        clReleaseKernel(kernel);
        clReleaseProgram(program);
        clReleaseCommandQueue(commandQueue);
        clReleaseContext(context);

        return totalOccurrences;
    }

    private static void writeResultsToCSV(String filePath, List<String[]> data) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            // Cabeçalho
            writer.println("Amostra,Metodo,Ocorrencias,Tempo(ms)");

            // Dados
            for (String[] result : data) {
                writer.println(String.join(",", result));
            }
        } catch (IOException e) {
            System.err.println("Erro ao escrever no arquivo CSV: " + e.getMessage());
        }
    }
}
