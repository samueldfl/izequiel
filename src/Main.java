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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jocl.*;
import static org.jocl.CL.*;

public class Main {
    private static final int NUM_THREADS = 4;
    private static final String TARGET_WORD = "and";
    private static final String OUTPUT_PATH = "./resources/results.csv";

    private static final List<File> FILES = Arrays.asList(
            new File("resources/DonQuixote-388208.txt"),
            new File("resources/Dracula-165307.txt"),
            new File("resources/MobyDick-217452.txt"));

    public static void main(String[] args) {
        List<List<String>> results = new ArrayList<>();

        for (File file : FILES) {
            final List<String> resultSerial = serialCPU(file, TARGET_WORD);
            results.add(resultSerial);

            final List<String> resultParallelCpu = parallelCPU(file, TARGET_WORD, NUM_THREADS);
            results.add(resultParallelCpu);

            final List<String> resultParallelGpu = parallelGPU(file, TARGET_WORD);
            results.add(resultParallelGpu);
        }

        results.forEach(result -> {
            String method = result.get(0);
            String occurrences = result.get(2);
            String duration = result.get(3);
            System.out.printf("%s: %s ocorrências em %s ms%n", method, occurrences, duration);
        });

        resultsToCSV(OUTPUT_PATH, results);
    }

    private static List<String> serialCPU(final File file, final String target) {
        final List<String> result = new ArrayList<>();

        final long startTime = System.currentTimeMillis();
        final long countSerial = countWordSerialCPU(file, target);
        final long endTime = System.currentTimeMillis();
        final long durationInMs = (endTime - startTime);

        result.add("SerialCPU");
        result.add(file.getName());
        result.add(String.valueOf(countSerial));
        result.add(String.valueOf(durationInMs));

        return result;
    }

    private static List<String> parallelCPU(final File file, String target, final int numThreads) {
        final List<String> result = new ArrayList<>();

        final long startTime = System.currentTimeMillis();
        final long countWords = countWordParallelCPU(file, target, numThreads);
        final long endTime = System.currentTimeMillis();
        final long durationInMs = (endTime - startTime);

        result.add("ParallelCPU");
        result.add(file.getName());
        result.add(String.valueOf(countWords));
        result.add(String.valueOf(durationInMs));

        return result;
    }

    private static List<String> parallelGPU(final File file, final String target) {
        final List<String> result = new ArrayList<>();

        final long startTime = System.currentTimeMillis();
        final int countParallelGPU = countWordParallelGPU(file.getPath(), target);
        final long endTime = System.currentTimeMillis();
        final long durationInMs = (endTime - startTime);

        result.add("ParallelGPU");
        result.add(file.getName());
        result.add(String.valueOf(countParallelGPU));
        result.add(String.valueOf(durationInMs));

        return result;
    }

    private static long countWordSerialCPU(final File file, final String target) {
        long occur = 0;

        final Pattern p = Pattern.compile("\\b(" + Pattern.quote(target) + ")\\b", Pattern.CASE_INSENSITIVE);

        try (final BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = p.matcher(line);
                while (m.find()) {
                    occur++;
                }
            }
        } catch (final IOException e) {
            System.err.println("Erro ao processar o arquivo: " + e.getMessage());
        }

        return occur;
    }

    private static long countWordParallelCPU(final File file, final String target, final int numThreads) {
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        long occur = 0;

        final Pattern p = Pattern.compile("\\b(" + Pattern.quote(target) + ")\\b", Pattern.CASE_INSENSITIVE);

        try (final BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            final BlockingQueue<Future<Integer>> futures = new LinkedBlockingQueue<>();

            while ((line = reader.readLine()) != null) {
                final String currentLine = line.toLowerCase();
                Future<Integer> future = executor.submit(() -> {
                    int count = 0;
                    Matcher m = p.matcher(currentLine);
                    while (m.find()) {
                        count++;
                    }
                    return count;
                });
                futures.add(future);
            }

            for (final Future<Integer> future : futures) {
                try {
                    occur += future.get();
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

        return occur;
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

        text = text.toLowerCase();
        targetWord = targetWord.toLowerCase();

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
        cl_kernel kernel = clCreateKernel(program, "counter", null);

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
        for (int c : occurrences) {
            totalOccurrences += c;
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

    private static void resultsToCSV(final String filePath, final List<List<String>> data) {
        try (final PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("Arquivo,Metodo,Ocorrências,Tempo de Execução (ms)");
            for (final List<String> result : data) {
                writer.println(String.join(",", result));
            }
        } catch (final IOException e) {
            System.err.println("Erro ao escrever no arquivo CSV: " + e.getMessage());
        }
    }
}
