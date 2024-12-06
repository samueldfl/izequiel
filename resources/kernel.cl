__kernel void counter(__global const char* text,
                        __global const char* word,
                        __global int* occurrences,
                        const int textLength,
                        const int wordLength)
{
    int i = get_global_id(0);

    // Se não cabe a palavra inteira a partir deste índice
    if (i + wordLength > textLength) {
        occurrences[i] = 0;
        return;
    }

    // Verifica se corresponde exatamente a "AND"
    for (int j = 0; j < wordLength; j++) {
        if (text[i + j] != word[j]) {
            occurrences[i] = 0;
            return;
        }
    }

    // Agora verifica as fronteiras.
    // Queremos garantir que antes e depois não haja [A-Za-z].
    // Verifica caractere anterior
    if (i > 0) {
        char cLeft = text[i - 1];
        if ((cLeft >= 'A' && cLeft <= 'Z') || (cLeft >= 'a' && cLeft <= 'z')) {
            occurrences[i] = 0;
            return;
        }
    }

    // Verifica caractere posterior
    int rightIndex = i + wordLength;
    if (rightIndex < textLength) {
        char cRight = text[rightIndex];
        if ((cRight >= 'A' && cRight <= 'Z') || (cRight >= 'a' && cRight <= 'z')) {
            occurrences[i] = 0;
            return;
        }
    }

    occurrences[i] = 1;
}
