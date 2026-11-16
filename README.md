# TRABAJO PRÁCTICO DE IMPLEMENTACIÓN: ESTEGANOGRAFÍA

Implementación de `stegobmp` con Java 21, Maven y picocli.
Permite ocultar y extraer archivos con LSB1, LSB4 y LSBI, con o sin cifrado, en BMP V3 de
24 bits sin compresión. También acepta BMP V4 y V5 (encabezado DIB más largo), ubicando los
píxeles con el offset del encabezado: uno de los archivos de la cátedra (`matrix_.bmp`) es V5.
`Tools/estegoanalisis.py` ayuda con el estegoanálisis.

## Estructura

En `stegobmp/src/main/java/stegobmp/`: `Main` inicia el programa, `cli/` valida
argumentos y `StegoService` coordina las operaciones. `bmp/` lee y escribe BMP,
`payload/` arma e interpreta el formato oculto, `steg/` contiene los algoritmos
y `crypto/` cifra y descifra con JCE de forma compatible con OpenSSL. Los tests están en `stegobmp/src/test/java/` y la consigna en `docs/`.

## Compilación y ejecución

Se requiere JDK 21, con `java` y `javac` disponibles en el `PATH`, y acceso a
Internet para la primera descarga de Maven y las dependencias. Maven Wrapper
utiliza la versión indicada en `maven-wrapper.properties`.

### Compilación

Desde la raíz del repositorio:

```shell
cd stegobmp
```

```shell
chmod u+x mvnw
chmod u+w . 
./mvnw clean package
```

Este comando compila el proyecto completo y ejecuta los tests.

### Ejecución

Para ejecutar la distribución `stegobmp.tar.gz`, desde el
directorio `stegobmp/`:

#### Shell

```shell
cd target
```

```shell
tar -xzf stegobmp.tar.gz
```

```shell
cd stegobmp
```

El script se ejecuta como `./stegobmp`. A continuación se
reproducen los ejemplos de las secciones 5.1 y 5.2 de la
[consigna](docs/Trabajo%20Practico%20Implementacion-2026_2.md), usando guiones
ASCII (`-`) y comillas rectas (`"`) para que puedan copiarse a la terminal.

**Ocultar con contraseña:** archivo `mensaje1.txt`, portador `imagen1.bmp`,
salida `imagenmas1.bmp`, algoritmo LSBI y cifrado 3DES en modo CBC.

```shell
./stegobmp -embed -in "mensaje1.txt" -p "imagen1.bmp" -out "imagenmas1.bmp" \
  -steg LSBI -a 3des -m cbc -pass "oculto"
```

**Ocultar sin contraseña:** el mismo archivo y portador, con LSBI.

```shell
./stegobmp -embed -in "mensaje1.txt" -p "imagen1.bmp" -out "imagenmas1.bmp" \
  -steg LSBI
```

**Extraer el archivo del ejemplo con contraseña:** usar el mismo algoritmo,
modo y contraseña con los que se ocultó.

```shell
./stegobmp -extract -p "imagenmas1.bmp" -out "mensaje1" \
  -steg LSBI -a 3des -m cbc -pass "oculto"
```

Los dos ejemplos de ocultamiento son alternativas: escriben la misma salida.
Las rutas de los archivos se interpretan respecto del directorio desde el que se
invoca el programa; también se pueden indicar rutas absolutas.

## Reglas del parser

- Exactamente una operación: `-embed` oculta un archivo; `-extract` lo recupera;
  `-dump` vuelca todos los bytes ocultos sin interpretar el payload ni descifrar.
- `-p`, `-out` y `-steg` son obligatorios en las tres operaciones.
- `-p`: ruta del BMP portador.
- `-out`: ruta con nombre del BMP de salida al ocultar; al extraer, nombre base
  al que se agrega la extensión recuperada; con `-dump`, archivo binario de salida
  con tantos bytes como la capacidad del portador. La carpeta debe existir.
- `-in`: archivo a ocultar; obligatorio con `-embed` y no se admite con las otras operaciones.
- `-steg`: algoritmo de esteganografía: `LSB1`, `LSB4` o `LSBI`.
- `-a`: algoritmo de cifrado: `aes128`, `aes192`, `aes256` o `3des`; por defecto `aes128`.
- `-m`: modo de cifrado: `ecb`, `cfb`, `ofb` o `cbc`; por defecto `cbc`.
- `-pass`: contraseña para cifrar o descifrar.
- Solo la presencia de `-pass` habilita cifrado/descifrado en la configuración.
  Sin contraseña, `-a` y `-m` no lo habilitan. Una contraseña explícita no puede estar vacía.
- Se respetan mayúsculas y minúsculas. Se rechazan opciones desconocidas,
  opciones repetidas, valores faltantes, rutas vacías y argumentos sueltos.
- `-h` y `--help` muestran la ayuda.

## Cifrado

Se implementa con JCE (incluido en el JDK) y produce el mismo resultado que
`openssl enc -<cifrado> -pbkdf2 -iter 10000 -md sha256 -S 0000000000000000 -pass pass:<password>`.

- Clave e IV: PBKDF2 con HMAC-SHA256, 10000 iteraciones y salt de 8 bytes en cero.
  De la salida se toman primero los bytes de la clave y luego los del IV.
- Algoritmos: `aes128`, `aes192`, `aes256` (IV de 16 bytes) y `3des`
  (DES-EDE3 con clave de 24 bytes e IV de 8).
- Modos: `ecb` y `cbc` con padding PKCS5; `cfb` es CFB de 8 bits (`cfb8` en
  OpenSSL) y `ofb` usa el tamaño de bloque (128 bits en AES); estos dos no usan padding.
- Se oculta `tamaño del cifrado || cifrado(tamaño real || datos || extensión)`.
- Si la contraseña, el algoritmo o el modo no coinciden con los usados al ocultar,
  la extracción falla con un mensaje de error y no escribe la salida.

## Esteganografía

En los tres métodos se usa el cuerpo del BMP en el orden físico del archivo (incluidos
los bytes de padding de fila) y los bits de cada byte oculto van del más al menos significativo.

- `LSB1`: un bit por byte del portador. Capacidad: `cuerpo / 8` bytes.
- `LSB4`: cuatro bits por byte del portador. Capacidad: `cuerpo / 2` bytes.
- `LSBI` (Majeed y Sulaiman): los primeros 4 bytes del cuerpo guardan con LSB1 común si se
  invirtieron los patrones `00`, `01`, `10` y `11`, en ese orden (el patrón de un byte son su
  tercer y segundo bit menos significativos). Desde el quinto byte se oculta un bit por byte
  usando solo azul y verde: se saltea el rojo, es decir, los bytes cuya posición en el cuerpo
  es 2 módulo 3. Un patrón se invierte cuando el LSB común cambiaría más bytes de ese patrón de
  los que dejaría iguales. Capacidad: `(cuerpo - cuerpo / 3 - 3) / 8` bytes.

Si el archivo no cabe, el programa informa la capacidad máxima del portador en bytes,
contando los 4 bytes del tamaño y la extensión.

## Estegoanálisis

Primero compilá y descomprimí la distribución según las instrucciones anteriores.
El script invoca `stegobmp -dump` para probar cada algoritmo; no implementa LSB en Python.
Busca el ejecutable en `stegobmp/target/stegobmp/stegobmp`, relativo al repositorio,
y luego en el `PATH`. Si está en otra ubicación, usá `--stegobmp /ruta/al/stegobmp`.

```shell
python3 Tools/estegoanalisis.py -o salida archivo1.bmp archivo2.bmp
```

Para cada BMP prueba LSB1, LSB4 y LSBI: si el tamaño leído es coherente y encuentra la
extensión, extrae el archivo; si el tamaño es coherente pero no hay extensión, indica que
probablemente esté cifrado. También avisa si el archivo tiene bytes de más después de los píxeles.
