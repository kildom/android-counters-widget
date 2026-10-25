<?php

class BackendException extends RuntimeException
{
    private $httpStatus;

    public function __construct($message, $httpStatus)
    {
        parent::__construct($message);
        $this->httpStatus = $httpStatus;
    }

    public function getHttpStatus()
    {
        return $this->httpStatus;
    }
}

function respond($httpStatus, array $payload)
{
    http_response_code($httpStatus);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    exit;
}

function isValidDate($date)
{
    if (!is_string($date) || !preg_match('/^(\d{4})-(\d{2})-(\d{2})$/D', $date, $matches)) {
        return false;
    }

    return checkdate((int) $matches[2], (int) $matches[3], (int) $matches[1]);
}

function readCounters($handle)
{
    if (!rewind($handle)) {
        throw new BackendException('Could not read the counter database.', 500);
    }

    $contents = stream_get_contents($handle);
    if ($contents === false) {
        throw new BackendException('Could not read the counter database.', 500);
    }

    try {
        $counters = json_decode($contents, true, 512, JSON_THROW_ON_ERROR);
    } catch (JsonException $exception) {
        throw new BackendException('The counter database contains invalid JSON.', 500);
    }

    if (!is_array($counters) || !array_is_list($counters)) {
        throw new BackendException('The counter database must contain a JSON array.', 500);
    }

    foreach ($counters as $counter) {
        if (
            !is_array($counter)
            || !isset($counter['id'])
            || !is_string($counter['id'])
            || !isset($counter['name'])
            || !is_string($counter['name'])
            || !isset($counter['period'])
            || !is_int($counter['period'])
            || $counter['period'] < 1
            || !isset($counter['targetDate'])
            || !isValidDate($counter['targetDate'])
        ) {
            throw new BackendException('The counter database contains an invalid counter.', 500);
        }
    }

    return $counters;
}

function writeCounters($handle, array $counters)
{
    try {
        $contents = json_encode($counters, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    } catch (JsonException $exception) {
        throw new BackendException('Could not encode the counter database.', 500);
    }

    if (!rewind($handle) || !ftruncate($handle, 0)) {
        throw new BackendException('Could not update the counter database.', 500);
    }

    $length = strlen($contents);
    $written = 0;
    while ($written < $length) {
        $count = fwrite($handle, substr($contents, $written));
        if ($count === false || $count === 0) {
            throw new BackendException('Could not update the counter database.', 500);
        }
        $written += $count;
    }

    if (!fflush($handle)) {
        throw new BackendException('Could not update the counter database.', 500);
    }
}

function requiredString(array $input, $field)
{
    if (!isset($input[$field]) || !is_string($input[$field]) || trim($input[$field]) === '') {
        throw new BackendException("The {$field} field must be a non-empty string.", 400);
    }

    return trim($input[$field]);
}

function requiredPeriod(array $input)
{
    if (!isset($input['period']) || !is_int($input['period']) || $input['period'] < 1) {
        throw new BackendException('The period field must be a positive integer.', 400);
    }

    return $input['period'];
}

$method = $_SERVER['REQUEST_METHOD'] ?? '';
$responseCommand = $method === 'GET' ? 'get' : '';
if ($method !== 'GET' && $method !== 'POST') {
    respond(405, [
        'command' => $responseCommand,
        'status' => 'error',
        'message' => 'Only GET and POST requests are supported.',
    ]);
}

$uid = $_GET['uid'] ?? null;
if (!is_string($uid) || !preg_match('/^[a-zA-Z0-9]+$/D', $uid)) {
    respond(403, [
        'command' => $responseCommand,
        'status' => 'error',
        'message' => 'A valid uid is required.',
    ]);
}

$databasePath = __DIR__ . '/data/' . $uid . '.json';
if (is_link($databasePath) || !is_file($databasePath)) {
    respond(403, [
        'command' => $responseCommand,
        'status' => 'error',
        'message' => 'The requested counter database does not exist.',
    ]);
}

$handle = @fopen($databasePath, $method === 'GET' ? 'r' : 'r+');
if ($handle === false) {
    respond(500, [
        'command' => $responseCommand,
        'status' => 'error',
        'message' => 'Could not open the counter database.',
    ]);
}

try {
    $lockType = $method === 'GET' ? LOCK_SH : LOCK_EX;
    if (!flock($handle, $lockType)) {
        throw new BackendException('Could not lock the counter database.', 500);
    }

    $counters = readCounters($handle);
    if ($method === 'GET') {
        $responseCommand = 'get';
        $response = [
            'command' => 'get',
            'status' => 'ok',
            'counters' => $counters,
        ];
    } else {
        $body = file_get_contents('php://input');
        if ($body === false) {
            throw new BackendException('Could not read the request body.', 400);
        }

        try {
            $decoded = json_decode($body, false, 512, JSON_THROW_ON_ERROR);
        } catch (JsonException $exception) {
            throw new BackendException('The request body must be valid JSON.', 400);
        }

        if (!$decoded instanceof stdClass) {
            throw new BackendException('The request body must be a JSON object.', 400);
        }

        $input = get_object_vars($decoded);
        $responseCommand = isset($input['command']) && is_string($input['command']) ? $input['command'] : '';
        if (!isset($input['command']) || !is_string($input['command'])) {
            throw new BackendException('The command field must be a string.', 400);
        }

        switch ($input['command']) {
            case 'edit':
                $id = requiredString($input, 'id');
                $index = null;
                foreach ($counters as $counterIndex => $counter) {
                    if ($counter['id'] === $id) {
                        $index = $counterIndex;
                        break;
                    }
                }
                if ($index === null) {
                    throw new BackendException('The requested counter was not found.', 404);
                }

                if (array_key_exists('name', $input)) {
                    $counters[$index]['name'] = requiredString($input, 'name');
                }
                if (array_key_exists('period', $input)) {
                    $counters[$index]['period'] = requiredPeriod($input);
                }
                if (array_key_exists('targetDate', $input)) {
                    if (!isValidDate($input['targetDate'])) {
                        throw new BackendException('The targetDate field must be a valid YYYY-MM-DD date.', 400);
                    }
                    $counters[$index]['targetDate'] = $input['targetDate'];
                }

                writeCounters($handle, $counters);
                $response = [
                    'command' => 'edit',
                    'status' => 'ok',
                    'counters' => $counters,
                ];
                break;

            case 'add':
                $name = requiredString($input, 'name');
                $period = requiredPeriod($input);
                try {
                    $targetDate = (new DateTimeImmutable('today'))->modify('+' . $period . ' days');
                } catch (Exception $exception) {
                    throw new BackendException('The period is too large to calculate a target date.', 400);
                }
                if ($targetDate === false || !isValidDate($targetDate->format('Y-m-d'))) {
                    throw new BackendException('The period is too large to calculate a target date.', 400);
                }

                do {
                    try {
                        $id = bin2hex(random_bytes(8));
                    } catch (Exception $exception) {
                        throw new BackendException('Could not generate a counter id.', 500);
                    }
                    $idExists = false;
                    foreach ($counters as $counter) {
                        if ($counter['id'] === $id) {
                            $idExists = true;
                            break;
                        }
                    }
                } while ($idExists);

                $counters[] = [
                    'id' => $id,
                    'name' => $name,
                    'period' => $period,
                    'targetDate' => $targetDate->format('Y-m-d'),
                ];
                writeCounters($handle, $counters);
                $response = [
                    'command' => 'add',
                    'status' => 'ok',
                    'counters' => $counters,
                    'id' => $id,
                ];
                break;

            case 'delete':
                $id = requiredString($input, 'id');
                $remaining = [];
                $found = false;
                foreach ($counters as $counter) {
                    if ($counter['id'] === $id) {
                        $found = true;
                        continue;
                    }
                    $remaining[] = $counter;
                }
                if (!$found) {
                    throw new BackendException('The requested counter was not found.', 404);
                }

                $counters = $remaining;
                writeCounters($handle, $counters);
                $response = [
                    'command' => 'delete',
                    'status' => 'ok',
                    'counters' => $counters,
                ];
                break;

            default:
                throw new BackendException('The requested command is not supported.', 400);
        }
    }
} catch (BackendException $exception) {
    $response = [
        'command' => $responseCommand,
        'status' => 'error',
        'message' => $exception->getMessage(),
    ];
    $responseStatus = $exception->getHttpStatus();
} finally {
    flock($handle, LOCK_UN);
    fclose($handle);
}

if (isset($responseStatus)) {
    respond($responseStatus, $response);
}
respond(200, $response);
