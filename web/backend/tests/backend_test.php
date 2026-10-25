<?php

function assertSameValue($expected, $actual, $message)
{
    if ($expected !== $actual) {
        throw new RuntimeException($message . "\nExpected: " . var_export($expected, true) . "\nActual: " . var_export($actual, true));
    }
}

function requestJson($url, $method = 'GET', $body = null)
{
    $headers = [];
    if ($method === 'POST') {
        $headers[] = 'Content-Type: application/json';
    }
    $context = stream_context_create([
        'http' => [
            'method' => $method,
            'header' => implode("\r\n", $headers),
            'content' => $body === null ? '' : $body,
            'ignore_errors' => true,
            'timeout' => 2,
        ],
    ]);
    $responseBody = @file_get_contents($url, false, $context);
    if ($responseBody === false) {
        return [0, null];
    }

    $status = 0;
    foreach ($http_response_header as $header) {
        if (preg_match('/^HTTP\/\S+ (\d{3})/', $header, $matches)) {
            $status = (int) $matches[1];
        }
    }
    $payload = json_decode($responseBody, true);
    if (!is_array($payload)) {
        throw new RuntimeException('The backend returned invalid JSON: ' . $responseBody);
    }

    return [$status, $payload];
}

function expectSuccess($result, $command)
{
    [$status, $payload] = $result;
    assertSameValue(200, $status, $command . ' should return HTTP 200.');
    assertSameValue($command, $payload['command'] ?? null, $command . ' response command should match.');
    assertSameValue('ok', $payload['status'] ?? null, $command . ' should return status ok.');
    return $payload;
}

function removeTestDirectory($directory)
{
    foreach (['backend.php', 'server.log'] as $filename) {
        $path = $directory . '/' . $filename;
        if (is_file($path)) {
            unlink($path);
        }
    }

    $dataFile = $directory . '/data/testuid.json';
    if (is_file($dataFile)) {
        unlink($dataFile);
    }
    if (is_dir($directory . '/data')) {
        rmdir($directory . '/data');
    }
    rmdir($directory);
}

$sourceBackend = dirname(__DIR__) . '/backend.php';
$temporaryDirectory = sys_get_temp_dir() . '/liczniki-backend-test-' . bin2hex(random_bytes(8));
$serverProcess = null;

try {
    if (!mkdir($temporaryDirectory, 0700) || !mkdir($temporaryDirectory . '/data', 0700)) {
        throw new RuntimeException('Could not create the temporary test directory.');
    }
    if (!copy($sourceBackend, $temporaryDirectory . '/backend.php')) {
        throw new RuntimeException('Could not copy backend.php into the test directory.');
    }

    $initialCounters = [
        [
            'id' => '8f3a1',
            'name' => 'Counter 1',
            'period' => 10,
            'targetDate' => '2024-12-31',
        ],
        [
            'id' => '87b2c',
            'name' => 'Counter 2',
            'period' => 5,
            'targetDate' => '2024-11-30',
        ],
    ];
    file_put_contents($temporaryDirectory . '/data/testuid.json', json_encode($initialCounters, JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR));

    $socket = stream_socket_server('tcp://127.0.0.1:0', $errorCode, $errorMessage);
    if ($socket === false) {
        throw new RuntimeException('Could not reserve a local test port: ' . $errorMessage);
    }
    $address = stream_socket_get_name($socket, false);
    fclose($socket);
    $port = (int) substr(strrchr($address, ':'), 1);
    $baseUrl = 'http://127.0.0.1:' . $port . '/backend.php';

    $descriptors = [
        0 => ['file', '/dev/null', 'r'],
        1 => ['file', $temporaryDirectory . '/server.log', 'a'],
        2 => ['file', $temporaryDirectory . '/server.log', 'a'],
    ];
    $serverProcess = proc_open(
        [PHP_BINARY, '-S', '127.0.0.1:' . $port, '-t', $temporaryDirectory],
        $descriptors,
        $pipes,
        $temporaryDirectory
    );
    if (!is_resource($serverProcess)) {
        throw new RuntimeException('Could not start PHP development server.');
    }

    $ready = false;
    for ($attempt = 0; $attempt < 100; $attempt++) {
        [$status, $payload] = requestJson($baseUrl . '?uid=testuid');
        if ($status === 200 && ($payload['status'] ?? null) === 'ok') {
            $ready = true;
            break;
        }
        usleep(50000);
    }
    assertSameValue(true, $ready, 'PHP development server should start and accept requests.');
    assertSameValue($initialCounters, $payload['counters'], 'GET should return the stored counters.');

    $edited = expectSuccess(requestJson(
        $baseUrl . '?uid=testuid',
        'POST',
        json_encode([
            'command' => 'edit',
            'id' => '8f3a1',
            'name' => 'Renamed counter',
        ], JSON_THROW_ON_ERROR)
    ), 'edit');
    assertSameValue('Renamed counter', $edited['counters'][0]['name'], 'Edit should update the provided name.');
    assertSameValue(10, $edited['counters'][0]['period'], 'Edit should preserve omitted fields.');
    assertSameValue('2024-12-31', $edited['counters'][0]['targetDate'], 'Edit should preserve omitted target date.');

    $beforeAdd = new DateTimeImmutable('today');
    $added = expectSuccess(requestJson(
        $baseUrl . '?uid=testuid',
        'POST',
        json_encode([
            'command' => 'add',
            'name' => 'Counter 3',
            'period' => 7,
            'targetDate' => '2000-01-01',
        ], JSON_THROW_ON_ERROR)
    ), 'add');
    assertSameValue(true, isset($added['id']) && is_string($added['id']), 'Add should return the new counter id.');
    $newCounter = $added['counters'][2];
    assertSameValue($added['id'], $newCounter['id'], 'The new id should identify the appended counter.');
    assertSameValue('Counter 3', $newCounter['name'], 'Add should store the supplied name.');
    assertSameValue(7, $newCounter['period'], 'Add should store the supplied period.');
    assertSameValue($beforeAdd->modify('+7 days')->format('Y-m-d'), $newCounter['targetDate'], 'Add should calculate target date from today and period.');

    $deleted = expectSuccess(requestJson(
        $baseUrl . '?uid=testuid',
        'POST',
        json_encode([
            'command' => 'delete',
            'id' => '8f3a1',
        ], JSON_THROW_ON_ERROR)
    ), 'delete');
    assertSameValue(2, count($deleted['counters']), 'Delete should remove the selected counter.');
    assertSameValue(false, in_array('8f3a1', array_column($deleted['counters'], 'id'), true), 'Deleted counter should no longer be returned.');

    [$status, $error] = requestJson($baseUrl . '?uid=testuid', 'POST', '{"command":');
    assertSameValue(400, $status, 'Malformed JSON should return HTTP 400.');
    assertSameValue('error', $error['status'] ?? null, 'Malformed JSON should return an error response.');

    [$status, $error] = requestJson($baseUrl);
    assertSameValue(403, $status, 'A missing uid should return HTTP 403.');
    assertSameValue('error', $error['status'] ?? null, 'A missing uid should return an error response.');

    [$status, $error] = requestJson($baseUrl . '?uid=../testuid');
    assertSameValue(403, $status, 'A non-alphanumeric uid should return HTTP 403.');
    assertSameValue('error', $error['status'] ?? null, 'An invalid uid should return an error response.');

    [$status, $error] = requestJson($baseUrl . '?uid=missing');
    assertSameValue(403, $status, 'A missing database should return HTTP 403.');
    assertSameValue('error', $error['status'] ?? null, 'A missing database should return an error response.');

    echo "Backend integration test passed.\n";
} finally {
    if (is_resource($serverProcess)) {
        proc_terminate($serverProcess);
        proc_close($serverProcess);
    }
    if (is_dir($temporaryDirectory)) {
        removeTestDirectory($temporaryDirectory);
    }
}
