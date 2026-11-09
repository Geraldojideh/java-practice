# Java Practice

Practice problems with automatic input/output testing. You write a program called `Main.java`; the tester runs it against sample and extra test cases and tells you what passed.

## Before You Start (once)

You need a **JDK** (Java Development Kit), version 11 or newer. Check by running this in a terminal:

```sh
# platform agnostic
java -version
```

Both commands should print a version number. If `javac` is "not found", you only have the runtime and need to install a JDK.

## How to Solve Problems

1. **Clone the Repository** – Clone this repository to your computer, then open the folder in VS Code (File > Open Folder). Open the repository folder itself, the one that contains `Test.java`, not a folder above it:

   ```sh
   git clone <URL OF THIS REPOSITORY>
   ```
2. **Read the Problem** – Each problem has its own folder, for example `Unit_01/Chapter_01/Q_01_01_01/`. Open the `XX-XX-XX.md` file inside it. (In VS Code, `Ctrl+Shift+V` / `Cmd+Shift+V` shows it nicely formatted.)
3. **Write Your Code** – Open the `Main.java` in the same folder and implement your solution. Three rules:
   - The class must be called `Main`.
   - Leave the first line (`package ...;`) exactly as it is. It matches the folder name, and it is what lets VS Code run the right problem.
   - Read input with `Scanner(System.in)` and print with `System.out`.
4. **Try It** (optional) – Click **Run** above `public static void main` in VS Code. The program runs in the terminal at the bottom, where you can type its input. This is for experimenting; it does not check your answer.
5. **Test Your Solution** – In a terminal opened at the repository root (the folder containing `Test.java`), run:

   ```sh
   java Test.java XX-XX-XX
   ```

   **Or in VS Code:** open any file from the problem's folder, then press `Ctrl+Shift+B` (`Cmd+Shift+B` on Mac).

**NOTE:** Replace `XX-XX-XX` with the problem ID (e.g., `01-01-01`). The ID format is `Unit-Chapter-Question`, so `01-01-01` is the folder `Unit_01/Chapter_01/Q_01_01_01/`.

You can also run every problem you have started to check your overall progress:

```sh
java Test.java
```

**Do not print prompts** such as `Enter your name:`. The tests compare *everything* your program prints, so print only what the problem asks for.

### Understanding Test Output

- **Sample tests** run first. If a sample test fails, all further testing for that problem is aborted — fix the sample case before moving on.
- **Batch tests** (1, 2, 3, ...) run in order of difficulty. If a test fails within a batch, the remaining tests in that batch are skipped, but later batches still run.
- Each test has a **time limit** (2 seconds unless the problem says otherwise). `Time Limit Exceeded!` usually means an infinite loop, or a `Scanner` waiting for input the test never sends.
- **Compile errors** are shown with the line number and a `^` marker under the problem. If you change or delete the `package` line, the tester tells you exactly what the first line should be.
- If your program **crashes** (for example an exception), the error message is shown under *Error Output*, and the test fails even if the output looked right.

How your output is compared with the expected output:

- Capital letters, punctuation and the order of lines must match exactly.
- Extra spaces and blank lines at the start or end are ignored.
- Numbers with decimals are compared by value (`3.0` matches `3.00`). Whole numbers must match exactly.

## Getting New Problems

When new problems are added, open a terminal inside the repository folder and run:

```sh
git pull
```

New problem folders appear automatically and your own work is left alone.

If Git refuses and says your local changes "would be overwritten", a file you edited was also changed in the original. Copy your `Main.java` somewhere safe and ask your teacher for help before doing anything else.

## File Structure

```
java-practice/
│── Test.java        # The tester (you never edit this)
│── README.md
│── .gitignore
│── .vscode/tasks.json   # Makes Ctrl+Shift+B run the tester
│
├── Unit_01/
│   ├── README.md             # List of this unit's problems
│   ├── Chapter_01/
│   │   ├── Q_01_01_01/
│   │   │   ├── 01-01-01.md   # Problem details
│   │   │   ├── Main.java     # You write your solution here
│   │   ├── Q_01_01_02/
│   │   │   ├── ...
│
├── tests/
│   ├── 01-01-01/            # Test cases for one problem
│   │   ├── sample-01.in     # Sample test input
│   │   ├── sample-01.out    # Sample test expected output
│   │   ├── 1-01.in          # Batch 1, test case 1 input
│   │   ├── 1-01.out         # Batch 1, test case 1 expected output
│   │   ├── 2-01.in          # Batch 2, test case 1 input
│   │   ├── timeout.txt      # Optional time limit override (seconds)
│   │   ├── ...
```
