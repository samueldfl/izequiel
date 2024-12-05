__kernel void countWord(
    __global const char *text,
    __global const char *word,
    __global int *occurrences,
    const int textLength,
    const int wordLength) {
    
    int id = get_global_id(0); // ID único da thread
    int count = 0;

    // Comparar a palavra no intervalo do texto
    for (int i = id; i < textLength - wordLength + 1; i += get_global_size(0)) {
        int match = 1;
        for (int j = 0; j < wordLength; j++) {
            if (text[i + j] != word[j]) {
                match = 0;
                break;
            }
        }
        if (match) {
            count++;
        }
    }

    occurrences[id] = count;
}
